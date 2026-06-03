package com.qualityminds.lazyval.testkit.scenarios;


import com.qualityminds.lazyval.testkit.Testkit;
import com.qualityminds.lazyval.testkit.dependencies.Dependency;
import org.eclipse.collections.api.collection.ImmutableCollection;
import org.eclipse.collections.api.map.ImmutableMap;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

/**
 * A Scenario defines a testcase for a Lazyval SPI provider. Depending on the chosen testkit type (Java or Kotlin),
 * it provides a typed descriptor which contains all required and optional information needed by the internal
 * toolchains to run it.
 */
public sealed interface Scenario {

    /**
     * The name of the source file this scenario is based on.
     * @return the name of the source file
     */
    String name();

    /**
     * Java-specific Scenario to be used in {@link Testkit.Java} instance.
     * @param desc the descriptor containing required and optional information
     * @see #of(String, String...) factory method "of" for convenient creation
     */
    record Java(Scenario.Descriptor desc) implements Scenario {

        /**
         * {@inheritDoc}
         */
        @Override
        public String name(){
            return desc.source().getName();
        }

        /**
         * Creates a scenario factory for testing Java annotation processing.
         * @param source the source file to be tested, not null.
         * @param additionalSources additional source files needed to compile the test `source`. Can be omitted.
         * @return scenario factory for further configuration
         */
        public static ScenarioFactory<Java> of(String source, String... additionalSources){
            return new ScenarioFactory<>(Java::new, source, additionalSources);
        }

        /**
         * Default case for a class with an bean-convention getter accessor.
         * <pre>{@code
         * @LazyValue
         * public final class Isbn {
         *     private final String value;
         *
         *     private Isbn(String value) {
         *         this.value = value;
         *     }
         *
         *     public String getValue() {
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
         * @return new ScenarioFactory instance
         */
        public static ScenarioFactory<Java> isbn() {
            return Scenario.Java.of("scenarios/java/Isbn.java");
        }
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
         * @return new ScenarioFactory instance
         */
        public static ScenarioFactory<Java> quantity() {
            return Scenario.Java.of("scenarios/java/Quantity.java");
        }
        /**
         * Edge-Case: a sealed interface defining an inner ProductId to test inner types.
         * Furthermore, the ProductId record defines multiple factories where only <code>of(String value)</code> is relevant for Lazyval.
         * <pre>{@code
         * import com.qualityminds.lazyval.LazyValue;
         * import util.IdGenerator;
         * import java.util.UUID;
         *
         * public sealed interface Ids {
         *
         *     @LazyValue
         *     record ProductId(String value) implements Ids {
         *
         *         public static ProductId of(String value){
         *             if(value == null){
         *                 return null;
         *             }
         *             return new ProductId(value);
         *         }
         *
         *         public static ProductId createNew(IdGenerator generator){
         *             return new ProductId(generator.generateId());
         *         }
         *
         *         public static ProductId createNew(){
         *             return new ProductId(UUID.randomUUID().toString());
         *         }
         *     }
         * }
         * }</pre>
         * @return new ScenarioFactory instance
         */
        public static ScenarioFactory<Java> ids() {
            return Scenario.Java.of("scenarios/java/Ids.java", "util/IdGenerator.java");
        }


        /**
         * A record wrapping a non-primitive reference type (LocalDate).
         * <pre>{@code
         * import java.time.LocalDate;
         * import com.qualityminds.lazyval.LazyValue;
         *
         * @LazyValue
         * public record OrderDate(LocalDate value) {
         *     public OrderDate {
         *         if (value == null) {
         *             throw new IllegalArgumentException("OrderDate must not be null");
         *         }
         *         if(value.isAfter(LocalDate.now())){
         *             throw new IllegalArgumentException("OrderDate must not be in the future");
         *         }
         *     }
         * }
         * }</pre>
         * @return new ScenarioFactory instance
         */
        public static ScenarioFactory<Java> orderDate() {
            return Scenario.Java.of("scenarios/java/OrderDate.java");
        }

//        /**
//         * Combines all sources into a single scenario.
//         * @return new ScenarioFactory instance
//         */
        // FIXME needed to catch errors when multiple source are processed in one round
//        public static ScenarioFactory<Java> combined() {
//            return Scenario.Java.of();
//        }

        /**
         * All predefined sample scenarios, without any dependencies.
         * @return immutable list of all predefined scenarios
         */
        public static List<ScenarioFactory<Java>> all() {
            return List.of(isbn(), quantity(), ids(), orderDate());
        }
    }

    /**
     * Kotlin-specific Scenario to be used in {@link Testkit.Kotlin} instance.
     * @param desc the descriptor containing required and optional information
     * @see #of(String, String...) factory method "of" for convenient creation
     */
    record Kotlin(Scenario.Descriptor desc) implements Scenario {

        /**
         * {@inheritDoc}
         */
        @Override
        public String name(){
            return desc.source().getName();
        }

        /**
         * Creates a scenario factory for testing Java annotation processing.
         * @param source the source file to be tested, not null.
         * @param additionalSources additional source files needed to compile the test `source`. Can be omitted.
         * @return scenario factory for further configuration
         */
        public static ScenarioFactory<Kotlin> of(String source, String... additionalSources){
            return new ScenarioFactory<>(Kotlin::new, source, additionalSources);
        }

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
         * @return new ScenarioFactory instance
         */
        public static ScenarioFactory<Kotlin> isbn() {
            return Scenario.Kotlin.of("scenarios/kotlin/Isbn.kt");
        }
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
         * @return new ScenarioFactory instance
         */
        public static ScenarioFactory<Kotlin> quantity() {
            return Scenario.Kotlin.of("scenarios/kotlin/Quantity.kt");
        }
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
         * @return new ScenarioFactory instance
         */
        public static ScenarioFactory<Kotlin> nullableQuantity() {
            return Scenario.Kotlin.of("scenarios/kotlin/NullableQuantity.kt");
        }
        /**
         * Edge-Case: a sealed interface defining an inner ProductId to test inner types.
         * Furthermore, the ProductId record defines multiple factories where only <code>of(String value)</code> is relevant for Lazyval.
         * <pre>{@code
         * import com.qualityminds.lazyval.LazyValue
         * import java.util.UUID
         * import util.IdGenerator
         *
         * sealed interface Ids {
         *  @LazyValue
         *  class ProductId private constructor(val value: String) : Ids {
         *         companion object {
         *             @JvmStatic
         *             fun of(value: String): ProductId {
         *              require(value.isNotBlank()) { "ProductId cannot be blank" }
         *                 return ProductId(value)
         *          }
         *
         *          @JvmStatic
         *             fun createNew(): ProductId {
         *              return ProductId(UUID.randomUUID().toString())
         *          }
         *
         *          @JvmStatic
         *          fun createNew(generator: IdGenerator): ProductId {
         *              return ProductId(generator.generateId())
         *          }
         *      }
         *  }
         * }
         * }</pre>
         * @return new ScenarioFactory instance
         */
        public static ScenarioFactory<Kotlin> ids() {
            return Scenario.Kotlin.of("scenarios/kotlin/Ids.kt", "util/IdGenerator.java");
        }

        /**
         * A data class wrapping a non-primitive reference type (LocalDate).
         * <pre>{@code
         * @LazyValue
         * data class OrderDate(val date: LocalDate) {
         *     init {
         *         require(date != null) { "OrderDate date cannot be null" }
         *     }
         * }
         * }</pre>
         * @return new ScenarioFactory instance
         */
        public static ScenarioFactory<Kotlin> orderDate() {
            return Scenario.Kotlin.of("scenarios/kotlin/OrderDate.kt");
        }

        /**
         * All predefined sample scenarios, without any dependencies.
         * @return immutable list of all predefined scenarios
         */
        public static List<ScenarioFactory<Kotlin>> all() {
            return List.of(isbn(), quantity(), nullableQuantity(), ids(), orderDate());
        }
    }


    /**
     * Describes required and optional information for a scenario.
     * @param source the actual source file to process.
     * @param additionalSources any other source file relevant for the compilation (imports). Optional but must not contain null elements.
     * @param dependencies will be added to the classpath of the temporary project. Optional but must not contain null elements.
     * @param options will be added as a processor-option "lazyval.generators.disable" to the compilation. Optional but must not contain null elements.
     */
    record Descriptor(
            File source,
            ImmutableCollection<File> additionalSources,
            ImmutableCollection<Dependency> dependencies,
            ImmutableMap<String, String> options){

        /**
         * Creates a new Descriptor instance.
         * @param source the actual source file to process.
         * @param additionalSources any other source file relevant for the compilation (imports). Can be empty (optional) but must not contain null elements.
         * @param dependencies will be added to the classpath of the temporary project. Can be empty (optional) but must not contain null elements.
         * @param options will be added as a processor-option. Can be empty (optional).
         */
        public Descriptor {
            Objects.requireNonNull(source);
            Objects.requireNonNull(dependencies);
            Objects.requireNonNull(additionalSources);
            for (File f : additionalSources) {
                Objects.requireNonNull(f, "additional-sources cannot contain null elements");
            }
            for (Dependency d : dependencies) {
                Objects.requireNonNull(d, "dependencies cannot contain null elements");
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
