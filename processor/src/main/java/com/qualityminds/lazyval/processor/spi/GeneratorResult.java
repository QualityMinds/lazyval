package com.qualityminds.lazyval.processor.spi;

import java.util.Objects;

// tag::result[]
/**
 * The result of a generator invocation. The processor will write the actual file based on the result.
 */
public sealed interface GeneratorResult {
    /**
     * Describes a to-be generated Java file.
     * @param metadata metadata about the to-be generated file
     * @param contents the generated code
     */
    record Java(Metadata metadata, String contents) implements GeneratorResult {}

    /**
     * Can be used to signal that no file should be generated (for instance, when the generator only handles
     * wrapped values of a certain typeMirror)
     */
    record Nothing() implements GeneratorResult {}

    /**
     * Provides metadata for a generated file.
     * @param packageName the package name of the generated file
     * @param className the class name of the generated file
     */
    record Metadata(String packageName, String className){
        /**
         * Creates metadata for a generated file.
         * @param packageName the package name of the generated file, not null
         * @param className the class name of the generated file, not null
         */
        public Metadata {
            Objects.requireNonNull(packageName);
            Objects.requireNonNull(className);
        }

        /**
         * Constructs the FQN from package and class name.
         * @return the fully qualified name of the generated class
         */
        public String qualifiedName(){
            return packageName + "." + className;
        }
    }
}
// end::result[]
