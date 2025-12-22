package de.qualityminds.lazyval.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.FileSpec
import de.qualityminds.lazyval.LazyValue
import de.qualityminds.lazyval.collections.NonEmptySet
import de.qualityminds.lazyval.ksp.codegen.JavaFileSpec
import de.qualityminds.lazyval.ksp.spi.*
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream
import java.util.stream.StreamSupport

sealed interface KotlinOrJavaResult {
    data class Kotlin(val fileSpec: FileSpec, val sources: List<KSFile>) : KotlinOrJavaResult
    data class Java(val javaFileSpec: JavaFileSpec, val sources: List<KSFile>) : KotlinOrJavaResult
    object Nothing : KotlinOrJavaResult

    companion object {
        fun from(generatorResult: GeneratorResult, sources: List<KSFile>) = when(generatorResult) {
            is GeneratorResult.Java -> Java(generatorResult.fileSpec, sources)
            is GeneratorResult.Kotlin -> Kotlin(generatorResult.fileSpec, sources)
            GeneratorResult.Nothing -> Nothing
        }
    }
}


class LazyvalSymbolProcessor(
    private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {

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
                        ValidateElement.ValidateJarElement(validated)
                    }
                }
            }
            .toList()

        if (!lazyvalEnvironment.isJpaOnClasspath() && !lazyvalEnvironment.isMapstructOnClasspath()) {
            lazyvalEnvironment.warnMissingClasspath()
            return emptyList()
        }

        fun generateSingleFile(generator: SingleFileGenerator, elements: Set<ValidatedKspGeneratorElement>, userSettings: SpiGenerator.Settings): KotlinOrJavaResult {
            val result = generator.generateSingleFile(NonEmptySet.ofAll(elements), userSettings)
            return KotlinOrJavaResult.from(result, validatedElements.mapNotNull {
                when(it){
                    is ValidateElement.ValidateJarElement -> null
                    is ValidateElement.ValidatedSourceElement -> it.source
                }
            })
        }

        fun generateFiles(generator: FilePerTypeGenerator, element: ValidatedKspGeneratorElement, userSettings: SpiGenerator.Settings, source: KSFile?): KotlinOrJavaResult {
            val result = generator.generateFilePerType(element, userSettings)
            return KotlinOrJavaResult.from(result, source?.let { listOf(it) } ?: emptyList())
        }

        // will be empty in the second round
        if(validatedElements.isNotEmpty()) {
            loadGenerators()
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
                                is ValidateElement.ValidateJarElement -> null
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
                        is KotlinOrJavaResult.Kotlin -> writeKotlinFile(result.fileSpec, result.sources)
                        is KotlinOrJavaResult.Java -> writeJavaFile(result.javaFileSpec, result.sources)
                        KotlinOrJavaResult.Nothing -> { /* nothing to do */ }
                    }
                }
        }

        return emptyList()
    }

    private fun loadGenerators(): Stream<SpiGenerator> {
        val originalContextClassLoader: ClassLoader = Thread.currentThread().contextClassLoader;
        try {
            Thread.currentThread().contextClassLoader = this.javaClass.classLoader;

            val singleFileGenerators = ServiceLoader.load(SingleFileGenerator::class.java)
            val filePerTypeGenerators = ServiceLoader.load(FilePerTypeGenerator::class.java)

            val hasSingle = singleFileGenerators.iterator().hasNext()
            val hasMultiple = filePerTypeGenerators.iterator().hasNext()

            if (!hasSingle && !hasMultiple) {
                lazyvalEnvironment.warn("No Lazyval SPI providers found on classpath.")
                return Stream.empty()
            }

            val disabledByConfig = lazyvalEnvironment.disabledGenerators()

            val generators = Stream.of(
                singleFileGenerators,
                filePerTypeGenerators,
            ).flatMap { serviceLoader -> StreamSupport.stream(serviceLoader.spliterator(), false) }
                // TODO check for ID
            .filter{generator -> generator.requiredClasspath().stream().allMatch{fqn -> lazyvalEnvironment.isClassAvailable(fqn)}}
            .filter{generator -> !disabledByConfig.contains(generator.generatorId())}
            .toList()

            lazyvalEnvironment.info(
                "Lazyval Active Providers: " + generators.stream()
                    .map{ generator -> generator.generatorId() }
                    .collect(Collectors.joining(", ")))

            if (generators.isEmpty()) {
                lazyvalEnvironment.warnMissingClasspath()
            }

            return generators.stream()
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private fun writeKotlinFile(fileSpec: FileSpec, sourceFiles: List<KSFile>) {
        try {
            val dependencies = if (sourceFiles.isNotEmpty()) {
                Dependencies(true, *sourceFiles.toTypedArray())
            } else {
                Dependencies(true)
            }

            val file = environment.codeGenerator.createNewFile(
                dependencies = dependencies,
                packageName = fileSpec.packageName,
                fileName = fileSpec.name,
                extensionName = "kt"
            )
            file.write(fileSpec.toString().toByteArray())
            file.close()
            lazyvalEnvironment.info("Written Kotlin file '${fileSpec.packageName}.${fileSpec.name}'")
        } catch (e: kotlin.io.FileAlreadyExistsException) {
            // This is common in incremental builds; usually, we can ignore it
            // as the existing file is considered up-to-date by KSP
        } catch (e: Exception) {
            lazyvalEnvironment.error("Failed to write Kotlin file: ${e.message}")
            throw e
        }
    }

    // Since only one mapper file is generated for all sources, we pass in all sources to have
    // proper regeneration on incremental builds.
    private fun writeJavaFile(javaFileSpec: JavaFileSpec, sourceFiles: List<KSFile>) {
        try {
            val dependencies = if (sourceFiles.isNotEmpty()) {
                Dependencies(true, *sourceFiles.toTypedArray())
            } else {
                Dependencies(true)
            }

            javaFileSpec.writeTo(environment.codeGenerator, dependencies)
            lazyvalEnvironment.info("Written Java file '${javaFileSpec.packageName}.${javaFileSpec.fileName}'")
        } catch (e: Exception) {
            lazyvalEnvironment.error("Failed to write Java file: ${e.message}")
            throw e
        }
    }
}