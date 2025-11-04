package de.qualityminds.lazyval.processor.spi;

import javax.lang.model.element.TypeElement;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public sealed interface SpiGenerator permits SingleFileGenerator, MultipleFilesGenerator {

    /**
     * A short id/name of the generator. The id must only contain valid Java package characters.
     * <p>
     *     The id is used to extract config options from the processing-environment in the form of
     *     <i>"lazyval.generatorId.optionA"</i> or to disable the generator via the option
     *     <i>"lazyval.disabledGenerators"</i>.
     * </p>
     */
    String generatorId();

    /**
     * If the generator requires additional classes on the classpath, they can be listed here.
     * The processor will check if all required classes are available and only call the generator if this is the case.
     * @return list containing fully qualified class names.
     */
    Collection<String> requiredClasspath();

    /**
     * Settings provided by the user for this generator.
     * The map will only contain keys which have the current generators id infixed, e.g. *"lazyval.generatorId.optionA"*
     */
    record Settings(Map<String, String> options) {
        /**
         * Convenience method to retrieve a single config option.
         */
        public Optional<String> get(String key){
            return Optional.ofNullable(options.get(key));
        }
    }


    /**
     * Split the elements FQN into its constituents and returns the substring before the "layer-markers",
     * which will be the root package. If nothing matches, the whole FQN will be returned.
     */
    default String extractRootPackage(TypeElement element) {
        Set<String> layerPackages = Set.of("boundary", "control", "entity", "application", "infrastructure", "domain");
        Predicate<String> IS_NOT_LAYER_PACKAGE_AND_CLASS = part -> !layerPackages.contains(part) && !Character.isUpperCase(part.charAt(0));

        return Arrays.stream(element.getQualifiedName().toString().split("\\."))
                .takeWhile(IS_NOT_LAYER_PACKAGE_AND_CLASS)
                .collect(Collectors.joining("."));
    }
}
