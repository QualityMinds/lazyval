package com.qualityminds.lazyval.ksp.internal

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.qualityminds.lazyval.LazyValue
import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.ValidatedElement
import java.io.IOException
import java.util.*
import java.util.stream.Stream

private sealed interface InternalResult {
    data class Kotlin(val metadata: GeneratorResult.Metadata, val contents: String, val sources: List<KSFile>) : InternalResult
    data class Java(val metadata: GeneratorResult.Metadata, val contents: String, val sources: List<KSFile>) : InternalResult
    data class ServiceLoader(val spiType: GeneratorResult.Metadata, val providerType: GeneratorResult.Metadata, val sources: List<KSFile>) : InternalResult

    companion object {
        fun from(generatorResult: GeneratorResult, sources: List<KSFile>) = when(generatorResult) {
            is GeneratorResult.Java -> Java(generatorResult.metadata, generatorResult.contents, sources)
            is GeneratorResult.Kotlin -> Kotlin(generatorResult.metadata, generatorResult.contents, sources)
            is GeneratorResult.ServiceLoader -> ServiceLoader(generatorResult.spiType, generatorResult.providerType, sources)
        }
    }
}

/**
 * A KSP2 annotation Processor which delegates domain-primitives to code generators provided via SPI.
 *
 * A domain-primitive is a class annotated with [LazyValue] or configured
 * via the processor option `lazyval.values`.
 */
class LazyvalSymbolProcessor(
    private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {

    companion object {
        private val allProviderGenerators: List<Generator>

        init {
            val originalContextClassLoader = Thread.currentThread().contextClassLoader
            try {
                Thread.currentThread().contextClassLoader = LazyvalSymbolProcessor::class.java.classLoader

                allProviderGenerators = ServiceLoader.load(Generator::class.java)
                    .stream()
                    .map { it.get() }
                    .toList()
            } finally {
                Thread.currentThread().contextClassLoader = originalContextClassLoader
            }
        }
    }

    private lateinit var lazyvalEnvironment: LazyvalKspEnvironment
    // Additional state tracking is needed as KSP will run another round when files are created,
    // and in case classpath-elements are available, those will be processed again.
    // The processor should only be run once.
    var hasProcessed = false

    @Suppress("CyclomaticComplexMethod") // reads as a top-to-bottom pipeline, splitting would reduce clarity
    override fun process(resolver: Resolver): List<KSAnnotated> {
        try {
            lazyvalEnvironment = LazyvalKspEnvironment(environment, resolver)
        } catch (_: IllegalArgumentException) {
            return emptyList()
        }

        val annotatedSymbols: List<KSAnnotated> = resolver.getSymbolsWithAnnotation(LazyValue::class.qualifiedName!!).toList()
        val configuredElements = lazyvalEnvironment.configuredValues()

        val validatedElements: List<ValidatedElement> = (annotatedSymbols + configuredElements)
            .filterIsInstance<KSClassDeclaration>()
            .mapNotNull { classDecl ->
                lazyvalEnvironment.validateElement(classDecl)?.let { validated ->
                    if(classDecl.containingFile != null){
                        ValidatedElement.ValidatedSourceElement(validated, classDecl.containingFile!!)
                    }else {
                        // this happens for configured values from other jars (not part of compilation unit)
                        ValidatedElement.ValidatedJarElement(validated)
                    }
                }
            }
            .toList()

        if(validatedElements.isNotEmpty() && !hasProcessed) {
            validateOptions()
            getActiveGenerators()
                .flatMap { generator ->
                    val context = lazyvalEnvironment.createContext(validatedElements.first().element)
                    generator
                        .generate(NonEmptySet.ofAll(validatedElements.map { it.element }.toSet()), context)
                        .map{
                            InternalResult.from(it, validatedElements.mapNotNull { ve ->
                                when(ve){
                                    is ValidatedElement.ValidatedJarElement -> null
                                    is ValidatedElement.ValidatedSourceElement -> ve.source
                                }
                            })
                        }
                }
                .forEach { result ->
                    when(result) {
                        is InternalResult.Kotlin -> writeKotlinFile(result)
                        is InternalResult.Java -> writeJavaFile(result)
                        is InternalResult.ServiceLoader -> writeServiceLoaderFile(result)
                    }
                }
        }
        hasProcessed = true
        return emptyList()
    }

    private fun getActiveGenerators(): Stream<Generator> {
        val originalContextClassLoader: ClassLoader = Thread.currentThread().contextClassLoader;
        try {
            Thread.currentThread().contextClassLoader = this.javaClass.classLoader;

            if (allProviderGenerators.isEmpty()) {
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
                "Active Providers: " + generators.joinToString(", ") { it.generatorId() })

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
                fileName = fileSpec.metadata.className,
                extensionName = "kt"
            )
            file.write(fileSpec.contents.toByteArray())
            file.close()
            lazyvalEnvironment.info("Written Kotlin file '${fileSpec.metadata.qualifiedName}'")
        } catch (_: FileAlreadyExistsException) {
            // This is common in incremental builds; usually, we can ignore it
            // as the existing file is considered up-to-date by KSP
            // TODO double check if this is really no problem and if it can be solved
        } catch (e: IOException) {
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
                fileName = javaFileSpec.metadata.className,
                extensionName = "java"
            )

            file.write(javaFileSpec.contents.toByteArray())
            file.close()
            lazyvalEnvironment.info("Written Java file '${javaFileSpec.metadata.qualifiedName}'")
        } catch (_: FileAlreadyExistsException) {
            // This is common in incremental builds; usually, we can ignore it
            // as the existing file is considered up-to-date by KSP
            // TODO double check if this is really no problem and if it can be solved
        } catch (e: IOException) {
            lazyvalEnvironment.error("Failed to write Java file: ${e.message}")
            throw e
        }
    }

    private fun writeServiceLoaderFile(serviceLoaderResult: InternalResult.ServiceLoader) {
        try {
            val dependencies = if (serviceLoaderResult.sources.isNotEmpty()) {
                Dependencies(true, *serviceLoaderResult.sources.toTypedArray())
            } else {
                Dependencies(true)
            }

            val resourcePath = "META-INF/services/${serviceLoaderResult.spiType.qualifiedName}"
            val file = environment.codeGenerator.createNewFileByPath(
                dependencies = dependencies,
                path = resourcePath,
                extensionName = ""
            )
            file.write(serviceLoaderResult.providerType.qualifiedName.toByteArray())
            file.close()
            lazyvalEnvironment.info("Written META-INF/services/ '${serviceLoaderResult.spiType.qualifiedName}'")
        } catch (_: FileAlreadyExistsException) {
            // Incremental build — file already exists
        } catch (e: IOException) {
            lazyvalEnvironment.error("Failed to write ServiceLoader file: ${e.message}")
            throw e
        }
    }

    /**
     * In contrast to javac, KSP does not warn about unknown processor options.
     * This at least checks for unknown "lazyval" options.
     */
    private fun validateOptions() {
        val supportedOptions = setOf(
            LazyvalKspEnvironment.DISABLED_GENERATORS,
            LazyvalKspEnvironment.CONFIGURED_VALUES,
            LazyvalKspEnvironment.BASE_PACKAGE
        ) + allProviderGenerators.flatMap { it.supportedOptions() }.toSet()

        environment.options
            .filter { it.key.contains("lazyval.") }
            .filter { it.key !in supportedOptions }
            .forEach { (key, _) ->
                lazyvalEnvironment.warn("Unrecognized option for lazyval-processor: $key")
            }
    }
}