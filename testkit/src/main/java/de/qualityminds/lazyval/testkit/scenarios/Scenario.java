package de.qualityminds.lazyval.testkit.scenarios;


import de.qualityminds.lazyval.testkit.dependencies.Dependency;
import org.eclipse.collections.api.collection.ImmutableCollection;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

public sealed interface Scenario {

    /**
     * The name of the source file this scenario is based on.
     */
    String name();

    record Java(Scenario.Descriptor desc) implements Scenario {
        public static ScenarioFactory<Java> of(String source, String... additionalSources){
            return new ScenarioFactory<>(Java::new, source, additionalSources);
        }

        /**
         * Default case for a class with an accessor.
         * <pre>{@code
         * @LazyValue
         * public final class Isbn {
         *     private final String value;
         *
         *     private Isbn(String value) {
         *         this.value = value;
         *     }
         *
         *     public String value() {
         *         return value;
         *     }
         *
         *     public static Isbn parse(String value) {
         *         if (value == null || value.isBlank()) {
         *             throw new IllegalArgumentException("ISBN cannot be blank");
         *         }
         *         return new Isbn(value);
         *     }
         *
         *     @Override
         *     public boolean equals(Object obj) {
         *         if (this == obj) return true;
         *         if (obj == null || getClass() != obj.getClass()) return false;
         *         Isbn isbn = (Isbn) obj;
         *         return value.equals(isbn.value);
         *     }
         *
         *     @Override
         *     public int hashCode() {
         *         return value.hashCode();
         *     }
         * }
         * }</pre>
         */
        public static final ScenarioFactory<Java> Isbn = Scenario.Java.of("scenarios/java/Isbn.java");
        /**
         * Default record case wrapping a primitive int (which might need boxing in some generated code).
         * <pre>{@code
         * @LazyValue
         * public record Quantity(int value) {
         *     public Quantity {
         *         if (value < 0) {
         *             throw new IllegalArgumentException("Quantity must be greater than 0");
         *         }
         *     }
         * }
         * }</pre>
         */
        public static final ScenarioFactory<Java> Quantity = Scenario.Java.of("scenarios/java/Quantity.java");
        /**
         * Edge-Case: a record defining multiple factories where only <code>of(String value)</code> is relevant for Lazyval.
         * <pre>{@code
         * import java.util.UUID;
         * import de.qualityminds.lazyval.LazyValue;
         * import util.IdGenerator;
         *
         * @LazyValue
         * public record ProductId(String value) {
         *
         *     public static ProductId of(String value){
         *         if(value == null){
         *             return null;
         *         }
         *         return new ProductId(value);
         *     }
         *
         *     public static ProductId createNew(IdGenerator generator){
         *         return new ProductId(generator.generateId());
         *     }
         *
         *     public static ProductId createNew(){
         *         return new ProductId(UUID.randomUUID().toString());
         *     }
         * }
         * }</pre>
         */
        public static final ScenarioFactory<Java> ProductId = Scenario.Java.of("scenarios/java/ProductId.java", "util/IdGenerator.java");

        /**
         * All predefined sample scenarios, without any dependencies.
         */
        public static final List<ScenarioFactory<Java>> All = List.of(Isbn, Quantity, ProductId);

        public String name(){
            return desc.source().getName();
        }
    }

    record Kotlin(Scenario.Descriptor desc) implements Scenario {

        /**
         * Default case for a class with a immutable property.
         * <pre>{@code
         * @LazyValue
         * class Isbn private constructor(val value: String) {
         *     companion object {
         *         @JvmStatic
         *         fun parse(value: String): Isbn {
         *             require(value.isNotBlank()) { "ISBN cannot be blank" }
         *             return Isbn(value)
         *         }
         *     }
         * }
         * }</pre>
         */
        public static final ScenarioFactory<Kotlin> Isbn = Scenario.Kotlin.of("scenarios/kotlin/Isbn.kt");
        /**
         * Default data class case.
         * <pre>{@code
         * @LazyValue
         * data class Quantity(val value: Int) {
         *     init {
         *         require(value >= 0) { "Quantity must be greater than 0" }
         *     }
         * }
         * }</pre>
         */
        public static final ScenarioFactory<Kotlin> Quantity = Scenario.Kotlin.of("scenarios/kotlin/Quantity.kt");
        /**
         * A data class which uses a factory capable of returning null.
         * <pre>{@code
         * @LazyValue
         * class NullableQuantity private constructor(val value: Int) {
         *     init {
         *         require(value >= 0) { "Quantity must be greater than 0" }
         *     }
         *
         *     companion object {
         *         fun ofNullable(value: Int?): NullableQuantity?{
         *             return value?.let{
         *                 NullableQuantity(value)
         *             }
         *         }
         *     }
         * }
         * }</pre>
         */
        public static final ScenarioFactory<Kotlin> NullableQuantity = Scenario.Kotlin.of("scenarios/kotlin/NullableQuantity.kt");
        /**
         * Edge-Case: a class defining multiple factories where only <code>of(value: String)</code> is relevant for Lazyval.
         * <pre>{@code
         * import de.qualityminds.lazyval.LazyValue
         * import java.util.UUID
         * import util.IdGenerator
         *
         * @LazyValue
         * class ProductId private constructor(val value: String) {
         *     companion object {
         *         @JvmStatic
         *         fun of(value: String): ProductId {
         *             require(value.isNotBlank()) { "ProductId cannot be blank" }
         *             return ProductId(value)
         *         }
         *
         *         @JvmStatic
         *         fun createNew(): ProductId {
         *             return ProductId(UUID.randomUUID().toString())
         *         }
         *
         *         @JvmStatic
         *         fun createNew(generator: IdGenerator): ProductId {
         *             return ProductId(generator.generateId())
         *         }
         *     }
         * }
         * }</pre>
         */
        public static final ScenarioFactory<Kotlin> ProductId = Scenario.Kotlin.of("scenarios/kotlin/ProductId.kt", "util/IdGenerator.java");

        /**
         * All predefined sample scenarios, without any dependencies.
         */
        public static final List<ScenarioFactory<Kotlin>> All = List.of(Isbn, Quantity, NullableQuantity, ProductId);

        public static ScenarioFactory<Kotlin> of(String source, String... additionalSources){
            return new ScenarioFactory<>(Kotlin::new, source, additionalSources);
        }

        public String name(){
            return desc.source().getName();
        }
    }


    /**
     * TODO
     * @param source the actual source file to process.
     * @param additionalSources any other source file relevant for the compilation (imports).
     * @param dependencies will be added to the classpath of the temporary project
     * @param disabledGenerators will be added as processor-option "lazyval.disabledGenerators" to the compilation.
     */
    record Descriptor(
            File source,
            ImmutableCollection<File> additionalSources,
            ImmutableCollection<Dependency> dependencies,
            ImmutableCollection<String> disabledGenerators){

        public Descriptor {
            Objects.requireNonNull(source);
            Objects.requireNonNull(dependencies);
            Objects.requireNonNull(additionalSources);
            for (File f : additionalSources) {
                Objects.requireNonNull(f, "additional-sources cannot contain null elements");
            }
        }
    }


    /**
     * Loads a source-file from the resources folder of the given classloader.
     * To support Maven-Failsafe as well as IDEs, this method also handles JARs (Failsafe) by extracting the
     * contents to a temporary directory.
     * @param resource path within the resources folder, e.g. "scenarios/simple/Simple.java"
     * @return file pointing to the source file resource
     * @throws RuntimeException if the resource could not be found or loaded.
     */
    static File loadSource(String resource) {
        var classloader = Thread.currentThread().getContextClassLoader();
        var resourceUrl = classloader.getResource(resource);
        if (resourceUrl == null) {
            throw new RuntimeException("Resource not found: " + resource);
        }
        try {
            var uri = resourceUrl.toURI();

            // Check the scheme - "jar" means it's inside a JAR file
            if ("jar".equals(uri.getScheme())) {
                // Extract resource from JAR to a temporary directory with the correct filename
                // This is necessary because JavaCompiler requires the filename to match the class name
                String fileName = resource.substring(resource.lastIndexOf('/') + 1);

                // Create a temp directory to hold the file with its proper name
                File tempDir = Files.createTempDirectory("lazyval-testhelper").toFile();
                tempDir.deleteOnExit();

                File tempFile = new File(tempDir, fileName);
                tempFile.deleteOnExit();

                try (var input = classloader.getResourceAsStream(resource);
                     var output = new java.io.FileOutputStream(tempFile)) {
                    if (input == null) {
                        throw new RuntimeException("Resource not found: " + resource);
                    }
                    input.transferTo(output);
                }
                return tempFile;
            } else {
                // Regular file URL (IDE using target/classes)
                return new File(uri);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load resource: " + resource, e);
        }
    }
}
