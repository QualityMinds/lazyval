package com.qualityminds.lazyval.processor.spi;

import com.qualityminds.lazyval.LazyValue;
import com.qualityminds.lazyval.collections.NonEmptySet;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Element;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * SPI for lazyval generators.
 * <p>
 * Implementations be registered via ServiceLoader in {@code META-INF/services/com.qualityminds.lazyval.processor.spi.Generator}.
 */
// tag::generator[]
@ApiStatus.Experimental
public interface Generator {

    /**
     * A short id/name of the generator. The id must only contain valid Java package characters.
     * <p>
     *     The id is used to extract config options from the processing-environment in the form of
     *     <i>"lazyval.ID.optionA"</i> or to disable the generator via the option
     *     <i>"lazyval.generators.disable=ID"</i>.
     * </p>
     * @return the generator id
     */
    String generatorId();

    /**
     * If the generator requires additional classes on the classpath, they can be listed here.
     * The processor will check if all required classes are available and only call the generator if this is the case.
     * @return set containing fully qualified class names.
     */
    Set<String> requiredClasspath();

    /**
     * Provides the keys supported by this generator. At least an option to specify the target package should be
     * listed here.
     * The key must contain "lazyval." at some place in the key to distinguish it from other processors
     * @return set of supported options.
     */
    Set<String> supportedOptions();

    /**
     *
     * @return
     */
    default Set<String> supersedes() {
        return Collections.emptySet();
    }

    /**
     * Called only once with a list of all domain primitives annotated with {@link LazyValue}.
     *
     * @param elements all elements annotated with {@link LazyValue}. Guaranteed to be non-empty.
     * @param context  provided {@link Context}
     * @return a stream of {@link GeneratorResult}s.
     */
    Stream<GeneratorResult> generate(NonEmptySet<ValidatedGeneratorElement> elements, Context context);
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
        boolean isOnClasspath(String fqcn);

        /**
         * Provides access to the user-provided options.
         * @param key the key of the option to retrieve
         * @return the value of the option or empty if the option is not set.
         */
        Optional<String> getSetting(String key);

        /**
         * Inspects a class on the compile classpath for SPI validation purposes
         * (e.g. verifying that a user-supplied class name refers to a valid implementation
         * of a required interface).
         * @param fqcn fully qualified class name to inspect
         * @return structural information about the class, or empty if no such class is on the classpath.
         */
        Optional<ClassInspection> inspectClass(String fqcn);

        /**
         * If the user configured the generator-package override, it will be used.
         * In case no override is configured, the global base-package configuration is checked and combined with the generator default layer.
         * If nothing is configured, the package of the first domain-primitive is used as a last resort.
         * @param overridePackageOptionKey the option-key that overrides the full package for a particular generator
         * @param defaultLayer             when the base-package is configured, this is the default layer where the generator places the output (when null, the base-package is used)
         * @return the package name where this generator should place the generated files.
         */
        String generatorPackage(String overridePackageOptionKey, @Nullable String defaultLayer);

        /**
         * Prints an info message during the current generation process.
         * @param generator the generator instance issuing the message
         * @param message   the message to be logged
         */
        void logInfo(Generator generator, String message);

        /**
         * Prints a warning message during the current generation process.
         * @param generator the generator instance issuing the message
         * @param message   the message to be logged
         */
        void logWarning(Generator generator, String message);

        /**
         * Prints a warning message during the current generation process.
         * @param generator the generator instance issuing the message
         * @param element   the element associated with the message
         * @param message   the message to be logged
         */
        void logWarning(Generator generator, Element element, String message);

        /**
         * Prints an error message during the current generation process.
         * @param generator the generator instance issuing the message
         * @param message   the message to be logged
         */
        void logError(Generator generator, String message);

        /**
         * Prints an error message during the current generation process.
         * @param generator the generator instance issuing the message
         * @param element   the element associated with the message
         * @param message   the message to be logged
         */
        void logError(Generator generator, Element element, String message);

        /**
         * Structural information about a class on the compile classpath.
         */
        interface ClassInspection {

            /**
             * Tests whether the class declaration is accessible from code generated at the given package
             * within the current compilation unit. The class is accessible when it is {@code public}, or
             * when it is package-private (Java) or {@code internal} (Kotlin, same module) and located in
             * the given package.
             * @param packageName the package where the calling code is generated
             * @return true if the class can be referenced from {@code packageName}.
             */
            boolean isAccessibleFrom(String packageName);

            /**
             * Tests whether the class declares a no-argument constructor that can be invoked from code
             * generated at the given package within the current compilation unit. Visibility rules match
             * {@link #isAccessibleFrom(String)}.
             * @param packageName the package where the calling code is generated
             * @return true if a callable no-arg constructor exists.
             */
            boolean hasAccessibleNoArgConstructor(String packageName);

            /**
             * Checks whether the class is assignable to the given supertype or interface by erasure.
             * @param supertypeFqn fully qualified name of the supertype or interface to test against
             * @return true if the inspected class is assignable to {@code supertypeFqn} by erasure;
             * false when either type is not resolvable.
             */
            boolean isAssignableTo(String supertypeFqn);

            /**
             * Checks whether the class declaration carries the given annotation directly.
             * @param annotationFqn fully qualified name of the annotation to look up
             * @return true if the class declaration carries the given annotation directly.
             */
            boolean hasAnnotation(String annotationFqn);
        }
    }
    // end::context[]
}
// end::generator[]
