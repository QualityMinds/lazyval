package com.qualityminds.lazyval.processor.spi;

import com.qualityminds.lazyval.LazyValue;
import com.qualityminds.lazyval.collections.NonEmptySet;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;


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
     * @return list containing fully qualified class names.
     */
    Collection<String> requiredClasspath();

    /**
     * Provides the keys supported by this generator. At least an option to specify the target package should be
     * listed here.
     * The key must contain "lazyval." at some place in the key to distinguish it from other processors
     * @return set of supported options.
     */
    Set<String> supportedOptions();

    /**
     * Called only once with a list of all domain primitives annotated with {@link LazyValue}.
     *
     * @param elements all elements annotated with {@link LazyValue}. Guaranteed to be non-empty.
     * @param context  provided {@link Context}
     * @return {@link GeneratorResult.Java} for the generated file or {@link GeneratorResult.Nothing}
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
         * If the user configured the generator-package override, it will be used.
         * In case no override is configured, the global base-package configuration is checked and combined with the generator default layer.
         * If nothing is configured, the package of the first domain-primitive is used as a last resort.
         * @param overridePackageOptionKey the option-key that overrides the full package for a particular generator
         * @param defaultLayer             when the base-package is configured, this is the default layer where the generator places the output (when null, the base-package is used)
         */
        String generatorPackage(String overridePackageOptionKey, @Nullable String defaultLayer);
    }
    // end::context[]
}
// end::generator[]
