package de.qualityminds.lazyval.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.FileSpec
import de.qualityminds.lazyval.LazyValue
import de.qualityminds.lazyval.ksp.codegen.JavaFileSpec
import de.qualityminds.lazyval.ksp.spi.GeneratorResult
import de.qualityminds.lazyval.ksp.spi.MultipleFilesGenerator
import de.qualityminds.lazyval.ksp.spi.SingleFileGenerator
import de.qualityminds.lazyval.ksp.spi.SpiGenerator
import de.qualityminds.lazyval.ksp.spi.ValidateElementWithSource
import java.util.*
import java.util.stream.Stream
import java.util.stream.StreamSupport
import kotlin.jvm.java

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

        environment.logger.info("Files: ${resolver.getAllFiles().toList().map { it.fileName }}")
        val annotatedSymbols: Sequence<KSAnnotated> = resolver.getSymbolsWithAnnotation(LazyValue::class.qualifiedName!!)

        val validatedElements = annotatedSymbols
            .filterIsInstance<KSClassDeclaration>()
            .mapNotNull { classDecl ->
                lazyvalEnvironment.validateElement(classDecl)?.let { validated ->
                    ValidateElementWithSource(validated, classDecl.containingFile!!)
                }
            }
            .toList()

        fun generateSingleFile(generator: SingleFileGenerator, elements: List<ValidatedKspGeneratorElement>): KotlinOrJavaResult {
            val result = generator.generateSingleFile(elements, lazyvalEnvironment)
            return KotlinOrJavaResult.from(result, validatedElements.map { it.source })
        }

        fun generateFiles(generator: MultipleFilesGenerator, element: ValidatedKspGeneratorElement, source: KSFile): KotlinOrJavaResult {
            val result = generator.generateFilePerType(element, lazyvalEnvironment)
            return KotlinOrJavaResult.from(result, listOf(source))
        }


        // will be empty in the second round
        if(validatedElements.isNotEmpty()) {
            loadGenerators()
                .flatMap { generator ->
                    when (generator) {
                        is SingleFileGenerator -> Stream.of(
                            generateSingleFile(
                                generator,
                                validatedElements.map { it.element })
                        )

                        is MultipleFilesGenerator -> validatedElements.map {
                            generateFiles(
                                generator,
                                it.element,
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
        val multipleFilesGenerators = ServiceLoader.load(MultipleFilesGenerator::class.java)

        val hasSingle = singleFileGenerators.iterator().hasNext()
        val hasMultiple = multipleFilesGenerators.iterator().hasNext()

        if (!hasSingle && !hasMultiple) {
            lazyvalEnvironment.warn("No generators found")
            return Stream.empty()
        }

        return Stream.of(
            singleFileGenerators,
            multipleFilesGenerators,
        ).flatMap { serviceLoader -> StreamSupport.stream(serviceLoader.spliterator(), false) }
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