package com.qualityminds.lazyval.ksp.spi

import com.google.devtools.ksp.symbol.KSFile
import com.qualityminds.lazyval.collections.NonEmptySet
import org.jetbrains.annotations.ApiStatus
import java.util.stream.Stream

/**
 * Internal use only.
 */
@ApiStatus.Internal
sealed interface ValidatedElement {
    val element: ValidatedKspGeneratorElement

    data class ValidatedSourceElement(override val element: ValidatedKspGeneratorElement, val source: KSFile) : ValidatedElement
    data class ValidatedJarElement(override val element: ValidatedKspGeneratorElement) : ValidatedElement
}

// tag::result[]
/**
 * The result of a generator invocation. The processor will write the actual file based on the result.
 */
sealed interface GeneratorResult {
    /**
     * Describes a to-be generated Kotlin file.
     * @param metadata metadata about the to-be generated file
     * @param contents the generated code
     */
    data class Kotlin(val metadata: Metadata, val contents: String) : GeneratorResult
    /**
     * Describes a to-be generated Java file.
     * @param metadata metadata about the to-be generated file
     * @param contents the generated code
     */
    data class Java(val metadata: Metadata, val contents: String) : GeneratorResult
    /**
     * ServiceLoader entry to be created in META-INF/services. The processor will collect all such entries
     * and merge the same SPI-types into a single file.
     * @param spiType the interface being implemented
     * @param providerType the class implementing the interface
     */
    data class ServiceLoader(val spiType: Metadata, val providerType: Metadata) : GeneratorResult
    /**
     * Provides metadata about generated files.
     * @param packageName the package name of the generated file
     * @param className the name of the generated file
     */
    data class Metadata(val packageName: String, val className: String){
        /**
         * The fully qualified name of the generated file.
         */
        val qualifiedName: String = "$packageName.$className"
    }
}
// end::result[]

// tag::generator[]
@ApiStatus.Experimental
interface Generator {
    /**
     * A short id/name of the generator. The id must only contain valid Java package characters.
     *
     * The id is used to extract config options from the processing-environment in the form of
     * *"lazyval.ID.optionA"* or to disable the generator via the option
     * *"lazyval.generators.disable=ID"*.
     *
     */
    fun generatorId(): String

    /**
     * If the generator requires additional classes on the classpath, they can be listed here.
     * The processor will check if all required classes are available and only call the generator if this is the case.
     * @return list containing fully qualified class names.
     */
    fun requiredClasspath(): Collection<String>

    /**
     * Provides the keys supported by this generator. At least an option to specify the target package should be
     * listed here.
     * The key must contain "lazyval." at some place in the key to distinguish it from other processors
     * @return set of supported options.
     */
    fun supportedOptions(): Set<String>

    /**
     * Called only once with a list of all domain primitives annotated with [com.qualityminds.lazyval.LazyValue].
     * @param validatedElements all elements annotated with [com.qualityminds.lazyval.LazyValue]. Guaranteed to be non-empty.
     * @param context provided [Context]
     * @return a stream of [GeneratorResult]s.
     */
    fun generate(
        validatedElements: NonEmptySet<ValidatedKspGeneratorElement>,
        context: Context
    ): Stream<GeneratorResult>
    // tag::context[]
    /**
     * Provides access to the current processing environment.
     */
    interface Context {
        /**
         * Checks for additional classes on the classpath to change the generator's behavior
         * @param fqcn fully qualified class name to look up
         * @return true if the class is on the classpath, false otherwise.
         */
        fun isOnClasspath(fqcn: String): Boolean

        /**
         * Provides access to the user-provided options.
         * @param key the key of the option to retrieve
         * @return the value of the option or empty if the option is not set.
         */
        fun getSetting(key: String): String?

        /**
         * If the user configured the generator-package override, it will be used.
         * In case no override is configured, the global base-package configuration is checked and combined with the generator default layer.
         * If nothing is configured, the package of the first domain-primitive is used as a last resort.
         * @param overridePackageOptionKey the option-key that overrides the full package for a particular generator
         * @param defaultLayer             when the base-package is configured, this is the default layer where the generator places the output (when null, the base-package is used)
         */
        fun generatorPackage(overridePackageOptionKey: String, defaultLayer: String?): String
    }
    // end::context[]
}
// end::generator[]
