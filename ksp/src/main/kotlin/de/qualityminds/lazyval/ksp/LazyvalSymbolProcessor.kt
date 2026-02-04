package de.qualityminds.lazyval.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import de.qualityminds.lazyval.LazyValue
import de.qualityminds.lazyval.collections.NonEmptySet
import de.qualityminds.lazyval.ksp.spi.*
import java.util.*
import java.util.stream.Stream

private sealed interface InternalResult {
    data class Kotlin(val metadata: GeneratorResult.Metadata, val contents: String, val sources: List<KSFile>) : InternalResult
    data class Java(val metadata: GeneratorResult.Metadata, val contents: String, val sources: List<KSFile>) : InternalResult
    object Nothing : InternalResult

    companion object {
        fun from(generatorResult: GeneratorResult, sources: List<KSFile>) = when(generatorResult) {
            is GeneratorResult.Java -> Java(generatorResult.metadata, generatorResult.contents, sources)
            is GeneratorResult.Kotlin -> Kotlin(generatorResult.metadata, generatorResult.contents, sources)
            GeneratorResult.Nothing -> Nothing
        }
    }
}


class LazyvalSymbolProcessor(
    private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {

    companion object {
        private val allProviderGenerators: List<SpiGenerator>

        init {
            val originalContextClassLoader = Thread.currentThread().contextClassLoader
            try {
                Thread.currentThread().contextClassLoader = LazyvalSymbolProcessor::class.java.classLoader

                val singleFileGenerators = ServiceLoader.load(SingleFileGenerator::class.java)
                val multipleFilesGenerators = ServiceLoader.load(FilePerTypeGenerator::class.java)

                allProviderGenerators = sequenceOf(singleFileGenerators, multipleFilesGenerators)
                    .flatMap { it.asSequence() }
                    .toList()
            } finally {
                Thread.currentThread().contextClassLoader = originalContextClassLoader
            }
        }
    }

    private lateinit var lazyvalEnvironment: LazyvalKspEnvironment

    override fun process(resolver: Resolver): List<KSAnnotated> {
        try {
            lazyvalEnvironment = LazyvalKspEnvironment(environment, resolver)
        } catch (_: IllegalArgumentException) {
            return emptyList()
        }

        val annotatedSymbols: Sequence<KSAnnotated> = resolver.getSymbolsWithAnnotation(LazyValue::class.qualifiedName!!)
        val configuredElements = lazyvalEnvironment.configuredValues()

        val validatedElements: List<ValidateElement> = (annotatedSymbols + configuredElements)
            .filterIsInstance<KSClassDeclaration>()
            .mapNotNull { classDecl ->
                lazyvalEnvironment.validateElement(classDecl)?.let { validated ->
                    if(classDecl.containingFile != null){
                        ValidateElement.ValidatedSourceElement(validated, classDecl.containingFile!!)
                    }else {
                        // this happens for configured values from other jars (not part of compilation unit)
                        ValidateElement.ValidatedJarElement(validated)
                    }
                }
            }
            .toList()

        fun generateSingleFile(generator: SingleFileGenerator, elements: Set<ValidatedKspGeneratorElement>, userSettings: SpiGenerator.Settings): InternalResult {
            val result = generator.generateSingleFile(NonEmptySet.ofAll(elements), userSettings)
            return InternalResult.from(result, validatedElements.mapNotNull {
                when(it){
                    is ValidateElement.ValidatedJarElement -> null
                    is ValidateElement.ValidatedSourceElement -> it.source
                }
            })
        }

        fun generateFiles(generator: FilePerTypeGenerator, element: ValidatedKspGeneratorElement, userSettings: SpiGenerator.Settings, source: KSFile?): InternalResult {
            val result = generator.generateFilePerType(element, userSettings)
            return InternalResult.from(result, source?.let { listOf(it) } ?: emptyList())
        }

        // will be empty in the second round
        if(validatedElements.isNotEmpty()) {
            validateOptions()
            getActiveGenerators()
                .flatMap { generator ->
                    val generatorOptions = environment.options
                        .filter { e -> e.key.startsWith("lazyval." + generator.generatorId() + ".") }

                    when (generator) {
                        is SingleFileGenerator -> Stream.of(
                            generateSingleFile(
                                generator,
                                validatedElements.map { it.element }.toSet(),
                                SpiGenerator.Settings(generatorOptions))
                        )

                        is FilePerTypeGenerator -> validatedElements.map {
                            val source = when(it){
                                is ValidateElement.ValidatedJarElement -> null
                                is ValidateElement.ValidatedSourceElement -> it.source
                            }
                            generateFiles(
                                generator,
                                it.element,
                                SpiGenerator.Settings(generatorOptions),
                                source
                            )
                        }.stream()
                    }
                }
                .forEach { result ->
                    when(result) {
                        is InternalResult.Kotlin -> writeKotlinFile(result)
                        is InternalResult.Java -> writeJavaFile(result)
                        InternalResult.Nothing -> { /* nothing to do */ }
                    }
                }
        }

        return emptyList()
    }

    private fun getActiveGenerators(): Stream<SpiGenerator> {
        val originalContextClassLoader: ClassLoader = Thread.currentThread().contextClassLoader;
        try {
            Thread.currentThread().contextClassLoader = this.javaClass.classLoader;

            val hasSingle: Boolean = allProviderGenerators.any { g -> g is SingleFileGenerator }
            val hasMultiple: Boolean = allProviderGenerators.any { g -> g is FilePerTypeGenerator }

            if (!hasSingle && !hasMultiple) {
                lazyvalEnvironment.warn("No Lazyval SPI providers found on classpath.")
                return Stream.empty()
            }

            val disabledByConfig = lazyvalEnvironment.disabledGenerators()

            val generators = allProviderGenerators.stream()
                // TODO check for ID
                .filter{generator -> generator.requiredClasspath().stream().allMatch{fqn -> lazyvalEnvironment.isClassAvailable(fqn)}}
                .filter{generator -> !disabledByConfig.contains(generator.generatorId())}
                .toList()

            lazyvalEnvironment.info(
                "Lazyval Active Providers: " + generators.joinToString(", ") { it.generatorId() })

            if (generators.isEmpty()) {
                lazyvalEnvironment.warnMissingClasspath()
            }

            return generators.stream()
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private fun writeKotlinFile(fileSpec: InternalResult.Kotlin) {
        try {
            val dependencies = if (fileSpec.sources.isNotEmpty()) {
                Dependencies(true, *fileSpec.sources.toTypedArray())
            } else {
                Dependencies(true)
            }

            val file = environment.codeGenerator.createNewFile(
                dependencies = dependencies,
                packageName = fileSpec.metadata.packageName,
                fileName = fileSpec.metadata.fileName,
                extensionName = "kt"
            )
            file.write(fileSpec.contents.toByteArray())
            file.close()
            lazyvalEnvironment.info("Written Kotlin file '${fileSpec.metadata.packageName}.${fileSpec.metadata.fileName}'")
        } catch (_: FileAlreadyExistsException) {
            // This is common in incremental builds; usually, we can ignore it
            // as the existing file is considered up-to-date by KSP
            // TODO double check if this is really no problem and if it can be solved
        } catch (e: Exception) {
            lazyvalEnvironment.error("Failed to write Kotlin file: ${e.message}")
            throw e
        }
    }

    // Since only one mapper file is generated for all sources, we pass in all sources to have
    // proper regeneration on incremental builds.
    private fun writeJavaFile(javaFileSpec: InternalResult.Java) {
        try {
            val dependencies = if (javaFileSpec.sources.isNotEmpty()) {
                Dependencies(true, *javaFileSpec.sources.toTypedArray())
            } else {
                Dependencies(true)
            }

            val file = environment.codeGenerator.createNewFile(
                dependencies = dependencies,
                packageName = javaFileSpec.metadata.packageName,
                fileName = javaFileSpec.metadata.fileName,
                extensionName = "java"
            )

            file.write(javaFileSpec.contents.toByteArray())
            file.close()
            lazyvalEnvironment.info("Written Java file '${javaFileSpec.metadata.packageName}.${javaFileSpec.metadata.fileName}'")
        } catch (_: FileAlreadyExistsException) {
            // This is common in incremental builds; usually, we can ignore it
            // as the existing file is considered up-to-date by KSP
            // TODO double check if this is really no problem and if it can be solved
        } catch (e: Exception) {
            lazyvalEnvironment.error("Failed to write Java file: ${e.message}")
            throw e
        }
    }

    /**
     * In contrast to javac, KSP does not warn about unknown processor options.
     * This at least checks for unknown "lazyval" options.
     */
    private fun validateOptions() {
        val supportedOptions = allProviderGenerators.flatMap { it.supportedOptions() }.toSet()

        environment.options
            .filter { it.key.contains("lazyval.") }
            .filter { it.key !in supportedOptions }
            .forEach { (key, _) ->
                lazyvalEnvironment.warn("Unrecognized option for lazyval-processor: $key")
            }
    }
}