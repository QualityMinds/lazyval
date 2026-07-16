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
import com.qualityminds.lazyval.ksp.spi.StockGeneratorIds
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
 * A domain-primitive is a class annotated with [LazyValue], or an external type listed in
 * `@LazyvalConfiguration.externalTypes` on the module's `package-info.java`.
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
    private lateinit var elementValidator: LazyvalKspElementValidator
    // Additional state tracking is needed as KSP will run another round when files are created,
    // and in case classpath-elements are available, those will be processed again.
    // The processor should only be run once.
    var hasProcessed = false

    @Suppress("CyclomaticComplexMethod") // reads as a top-to-bottom pipeline, splitting would reduce clarity
    override fun process(resolver: Resolver): List<KSAnnotated> {
        try {
            lazyvalEnvironment = LazyvalKspEnvironment(environment, resolver)
            elementValidator = LazyvalKspElementValidator(lazyvalEnvironment)
        } catch (_: IllegalArgumentException) {
            return emptyList()
        }

        val annotatedSymbols: List<KSAnnotated> = resolver.getSymbolsWithAnnotation(LazyValue::class.qualifiedName!!).toList()
        val configuredElements = lazyvalEnvironment.configuredValues()

        // Sort by qualified type name so the iteration order is deterministic across runs.
        // Generators that emit a single file containing entries for every element (Jackson modules, etc.)
        // rely on stable input order to produce byte-identical output; approval tests fail otherwise.
        // Multi-file generators are unaffected.
        val validatedElements: List<ValidatedElement> = (annotatedSymbols + configuredElements)
            .filterIsInstance<KSClassDeclaration>()
            .mapNotNull { classDecl ->
                elementValidator.validate(classDecl)?.let { validated ->
                    if(classDecl.containingFile != null){
                        ValidatedElement.ValidatedSourceElement(validated, classDecl.containingFile!!)
                    }else {
                        // this happens for configured values from other jars (not part of compilation unit)
                        ValidatedElement.ValidatedJarElement(validated)
                    }
                }
            }
            .sortedBy { it.element.element.qualifiedName?.asString().orEmpty() }

        if(validatedElements.isNotEmpty() && !hasProcessed) {
            validateOptions()
            val results = getActiveGenerators()
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
                }.toList()

                results.forEach { result ->
                    when (result) {
                        is InternalResult.Kotlin -> writeKotlinFile(result)
                        is InternalResult.Java -> writeJavaFile(result)
                        is InternalResult.ServiceLoader -> { /* handled below */ }
                    }
                }

                writeServiceLoaderFiles(results.filterIsInstance<InternalResult.ServiceLoader>())
        }
        hasProcessed = true
        return emptyList()
    }

    private fun getActiveGenerators(): Stream<Generator> {
        val originalContextClassLoader: ClassLoader = Thread.currentThread().contextClassLoader
        try {
            Thread.currentThread().contextClassLoader = this.javaClass.classLoader

            if (allProviderGenerators.isEmpty()) {
                lazyvalEnvironment.warn("No Lazyval SPI providers found on classpath.")
                return Stream.empty()
            }

            val disabledByConfig = lazyvalEnvironment.disabledGenerators()

            val candidates = allProviderGenerators
                .filter { g -> g.requiredClasspath().all { fqn -> lazyvalEnvironment.isClassAvailable(fqn) } }
                .filter { g -> g.generatorId() !in disabledByConfig }
                .toSet()

            val resolution = if(lazyvalEnvironment.isSupersedeEnabled()){
                GeneratorResolution.resolve(candidates)
            } else {
                GeneratorResolution.Result(candidates, emptySet())
            }

            resolution.superseded.forEach { s ->
                lazyvalEnvironment.info("'${s.id}' was auto-disabled because '${s.by}' supersedes it")
            }

            lazyvalEnvironment.info(
                "Active Providers: " + resolution.active.joinToString(", ") { it.generatorId() })

            val activeIds = resolution.active.map { it.generatorId() }.toSet()
            if (activeIds.contains(StockGeneratorIds.JACKSON_3) && activeIds.contains(StockGeneratorIds.JACKSON_2)) {
                lazyvalEnvironment.warn("Both 'jackson-2' and 'jackson-3' generators are active (probably due to transitive dependencies). " +
                        "This might be intentional, then ignore this warning. " +
                        "Otherwise, disable via one 'lazyval.generators.disable'")
            }

            if (resolution.active.isEmpty()) {
                lazyvalEnvironment.warnMissingClasspath()
            }

            return resolution.active.stream()
        } finally {
            Thread.currentThread().contextClassLoader = originalContextClassLoader
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

    private fun writeServiceLoaderFiles(serviceLoaderResults: List<InternalResult.ServiceLoader>) {
        // group all providers by spi-type in order to place them in a single file
        serviceLoaderResults
            .groupBy { it.spiType.qualifiedName }
            .forEach { (spiTypeName, group) ->
                val allSources = group.flatMap { it.sources }.distinct()
                val dependencies = if (allSources.isNotEmpty()) {
                    Dependencies(true, *allSources.toTypedArray())
                } else {
                    Dependencies(true)
                }

                val fileContent = group
                    .map { it.providerType.qualifiedName }
                    .distinct()
                    .joinToString("\n")

                try {
                    val file = environment.codeGenerator.createNewFileByPath(
                        dependencies = dependencies,
                        path = "META-INF/services/$spiTypeName",
                        extensionName = ""
                    )
                    file.write(fileContent.toByteArray())
                    file.close()
                    lazyvalEnvironment.info("Written 'META-INF/services/$spiTypeName' with ${group.size} provider(s)")
                } catch (_: FileAlreadyExistsException) {
                    // Incremental build — file already exists
                } catch (e: IOException) {
                    lazyvalEnvironment.error("Failed to write ServiceLoader file: ${e.message}")
                    throw e
                }
            }
    }

    /**
     * In contrast to javac, KSP does not warn about unknown processor options.
     * This at least checks for unknown "lazyval" options.
     */
    private fun validateOptions() {
        val supportedOptions = setOf(
            LazyvalKspEnvironment.DISABLED_GENERATORS,
            LazyvalKspEnvironment.SUPERSEDE_ENABLED,
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