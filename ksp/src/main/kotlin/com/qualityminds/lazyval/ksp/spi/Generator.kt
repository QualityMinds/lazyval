package com.qualityminds.lazyval.ksp.spi

import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSNode
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
     * @return set containing fully qualified class names.
     */
    fun requiredClasspath(): Set<String>

    /**
     * Provides the keys supported by this generator. At least an option to specify the target package should be
     * listed here.
     * The key must contain "lazyval." at some place in the key to distinguish it from other processors
     * @return set of supported options.
     */
    fun supportedOptions(): Set<String>

    /**
     * A generator can supersede other generators. For instance, 'spring-data' and 'cassandra' generators share the same
     * driver classpath which would normally activate both at the same time. Superseding solves that issue.
     * @return set of generators that this generator supersedes.
     */
    fun supersedes(): Set<String?> {
        return emptySet()
    }

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
         * Inspects a class on the compile classpath for SPI validation purposes
         * (e.g. verifying that a user-supplied class name refers to a valid implementation
         * of a required interface).
         * @param fqcn fully qualified class name to inspect
         * @return structural information about the class, or `null` if no such class is on the classpath.
         */
        fun inspectClass(fqcn: String): ClassInspection?

        /**
         * If the user configured the generator-package override, it will be used.
         * In case no override is configured, the global base-package configuration is checked and combined with the generator default layer.
         * If nothing is configured, the package of the first domain-primitive is used as a last resort.
         * @param overridePackageOptionKey the option-key that overrides the full package for a particular generator
         * @param defaultLayer             when the base-package is configured, this is the default layer where the generator places the output (when null, the base-package is used)
         */
        fun generatorPackage(overridePackageOptionKey: String, defaultLayer: String?): String

        /**
         * Prints an info message during the current generation process.
         * @param generator the generator instance issuing the message
         * @param message   the message to be logged
         */
        fun logInfo(generator: Generator, message: String)

        /**
         * Prints a warning message during the current generation process.
         * @param generator the generator instance issuing the message
         * @param message   the message to be logged
         */
        fun logWarning(generator: Generator, message: String)

        /**
         * Prints a warning message during the current generation process.
         * @param generator the generator instance issuing the message
         * @param element   the element associated with the message
         * @param message   the message to be logged
         */
        fun logWarning(
            generator: Generator,
            element: KSNode,
            message: String
        )

        /**
         * Prints an error message during the current generation process.
         * @param generator the generator instance issuing the message
         * @param message   the message to be logged
         */
        fun logError(generator: Generator, message: String)

        /**
         * Prints an error message during the current generation process.
         * @param generator the generator instance issuing the message
         * @param element   the element associated with the message
         * @param message   the message to be logged
         */
        fun logError(
            generator: Generator,
            element: KSNode,
            message: String
        )

        /**
         * Structural information about a class on the compile classpath.
         */
        interface ClassInspection {

            /**
             * Tests whether the class declaration is accessible from code generated at the given package
             * within the current compilation unit. The class is accessible when it is `public`, when it
             * is `internal` and originates in the current module, or when it is Java package-private and
             * located in the given package.
             * @param packageName the package where the calling code is generated
             * @return `true` if the class can be referenced from [packageName].
             */
            fun isAccessibleFrom(packageName: String): Boolean

            /**
             * Tests whether the class declares a no-argument constructor that can be invoked from code
             * generated at the given package within the current compilation unit. Visibility rules match
             * [isAccessibleFrom].
             * @param packageName the package where the calling code is generated
             * @return `true` if a callable no-arg constructor exists.
             */
            fun hasAccessibleNoArgConstructor(packageName: String): Boolean

            /**
             * Checks whether the class is assignable to the given supertype or interface by erasure.
             * @param supertypeFqn fully qualified name of the supertype or interface to test against
             * @return `true` if the inspected class is assignable to [supertypeFqn] by erasure;
             *         `false` when either type is not resolvable.
             */
            fun isAssignableTo(supertypeFqn: String): Boolean

            /**
             * Checks whether the class declaration carries the given annotation directly.
             * @param annotationFqn fully qualified name of the annotation to look up
             * @return `true` if the class declaration carries the given annotation directly.
             */
            fun hasAnnotation(annotationFqn: String): Boolean
        }
    }
    // end::context[]
}
// end::generator[]
