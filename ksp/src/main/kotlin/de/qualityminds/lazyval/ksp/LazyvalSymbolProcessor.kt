package de.qualityminds.lazyval.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.FileSpec
import de.qualityminds.lazyval.LazyValue
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

        if (!lazyvalEnvironment.isJpaOnClasspath() && !lazyvalEnvironment.isMapstructOnClasspath()) {
            lazyvalEnvironment.warnMissingClasspath()
            return emptyList()
        }

        val annotatedSymbols: Sequence<KSAnnotated> = resolver.getSymbolsWithAnnotation(LazyValue::class.qualifiedName!!)

        val validatedElements = annotatedSymbols
            .filterIsInstance<KSClassDeclaration>()
            .mapNotNull { classDecl ->
                lazyvalEnvironment.validateElement(classDecl)?.let { validated ->
                    ValidateElementWithSource(validated, classDecl.containingFile!!)
                }
            }
            .toList()

        fun generateSingleFile(generator: SingleFileGenerator, elements: Set<ValidatedKspGeneratorElement>, userSettings: SpiGenerator.Settings): KotlinOrJavaResult {
            val result = generator.generateSingleFile(NonEmptySet.fromSet(elements), userSettings)
            return KotlinOrJavaResult.from(result, validatedElements.map { it.source })
        }

        fun generateFiles(generator: FilePerTypeGenerator, element: ValidatedKspGeneratorElement, userSettings: SpiGenerator.Settings, source: KSFile): KotlinOrJavaResult {
            val result = generator.generateFilePerType(element, userSettings)
            return KotlinOrJavaResult.from(result, listOf(source))
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
                            generateFiles(
                                generator,
                                it.element,
                                SpiGenerator.Settings(generatorOptions),
                                it.source
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
        val singleFileGenerators = ServiceLoader.load(SingleFileGenerator::class.java)
        val filePerTypeGenerators = ServiceLoader.load(FilePerTypeGenerator::class.java)

        val hasSingle = singleFileGenerators.iterator().hasNext()
        val hasMultiple = filePerTypeGenerators.iterator().hasNext()

        if (!hasSingle && !hasMultiple) {
            lazyvalEnvironment.warn("No generators found")
            return Stream.empty()
        }

        val disabledByConfig = Arrays.stream<String>(
            environment.options
                .getOrDefault(LazyvalKspEnvironment.DISABLED_GENERATORS, "")
                .split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
            .map<String?> { obj: String? -> obj!!.trim { it <= ' ' } }
            .filter { s: String? -> !s!!.isEmpty() }
            .toList()

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
    }

    private fun writeKotlinFile(fileSpec: FileSpec, sourceFiles: List<KSFile>) {
        try {
            val dependencies = if (sourceFiles.isNotEmpty()) {
                Dependencies(true, *sourceFiles.toTypedArray())
            } else {
                Dependencies(false)
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
                Dependencies(false)
            }

            javaFileSpec.writeTo(environment.codeGenerator, dependencies)
            lazyvalEnvironment.info("Written Java file '${javaFileSpec.packageName}.${javaFileSpec.fileName}'")
        } catch (e: Exception) {
            lazyvalEnvironment.error("Failed to write Java file: ${e.message}")
            throw e
        }
    }
}