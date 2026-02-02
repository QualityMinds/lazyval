package de.qualityminds.lazyval.processor.spi;

import java.util.Objects;

public sealed interface GeneratorResult {
    record Java(Metadata metadata, String contents) implements GeneratorResult {}
    record Nothing() implements GeneratorResult {}

    /**
     * Provides metadata about generated files.
     */
    record Metadata(String packageName, String className){
        public Metadata {
            Objects.requireNonNull(packageName);
            Objects.requireNonNull(className);
        }
    }
}
