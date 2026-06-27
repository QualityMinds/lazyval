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
     * a short, human-readable label identifying this scenario in test reports
     * @return the display name of this scenario
     */
    String displayName();

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
        public String displayName(){
            return desc.name();
        }

        /**
         * Creates a scenario factory for testing Java annotation processing.
         * @param name the name describing this scenario
         * @param sources all files relevant for the compilation (imports). Must not contain null elements.
         * @return scenario factory for further configuration
         */
        public static ScenarioFactory<Java> of(String name, String... sources){
            return new ScenarioFactory<>(Java::new, name, sources);
        }

        /**
         * Single-source convenience; the name is derived from the source filename.
         * For multi-source scenarios, use {@link #of(String, String...)} with an explicit name.
         */
        public static ScenarioFactory<Java> ofSingle(String source) {
            return new ScenarioFactory<>(Java::new, ScenarioFactory.deriveName(source), source);
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
            return Scenario.Java.ofSingle("scenarios/java/Isbn.java");
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
            return Scenario.Java.ofSingle("scenarios/java/Quantity.java");
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
            return Scenario.Java.of("Ids.java with required IdGenerator.java",
                    "scenarios/java/Ids.java",
                    "util/IdGenerator.java");
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
            return Scenario.Java.of("OrderDate.java", "scenarios/java/OrderDate.java");
        }

        /**
         * Combines all sources into a single scenario.
         * @return new ScenarioFactory instance
         * @see #isbn()
         * @see #quantity()
         * @see #orderDate()
         * @see #ids()
         */
        public static ScenarioFactory<Java> combined() {
            return Scenario.Java.of("All predefined Java sources in one compilation",
                    "scenarios/java/Isbn.java",
                    "scenarios/java/Quantity.java",
                    "scenarios/java/OrderDate.java",
                    "scenarios/java/Ids.java"
            );
        }

        /**
         * All predefined sample scenarios, without any dependencies.
         * @return immutable list of all predefined scenarios
         * @see #isbn()
         * @see #quantity()
         * @see #orderDate()
         * @see #ids()
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
        public String displayName(){
            return desc.name();
        }

        /**
         * Creates a scenario factory for the Java annotation processor.
         *
         * @param displayName a short, human-readable label identifying this scenario in test reports,
         *                    {@code @Unroll} substitutions and failure messages — surfaced through
         *                    {@link Scenario#displayName()}. Prefer a noun phrase describing what makes
         *                    this set of inputs distinct from sibling scenarios in the same test file
         *                    (e.g. {@code "two-converters"}, {@code "no-noarg-ctor"}) rather than
         *                    restating the behavior under test. For single-source scenarios consider
         *                    {@link #ofSingle(String)}, which derives the display name from the source
         *                    filename.
         * @param sources classpath-relative paths of source files to compile together; order is
         *                irrelevant to the toolchain.
         * @return scenario factory for further configuration
         */
        public static ScenarioFactory<Kotlin> of(String displayName, String... sources){
            return new ScenarioFactory<>(Kotlin::new, displayName, sources);
        }

        /**
         * Single-source convenience; the name is derived from the source filename.
         * For multi-source scenarios, use {@link #of(String, String...)} with an explicit name.
         */
        public static ScenarioFactory<Kotlin> ofSingle(String source) {
            return new ScenarioFactory<>(Kotlin::new, ScenarioFactory.deriveName(source), source);
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
            return Scenario.Kotlin.ofSingle("scenarios/kotlin/Isbn.kt");
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
            return Scenario.Kotlin.ofSingle("scenarios/kotlin/Quantity.kt");
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
            return Scenario.Kotlin.ofSingle("scenarios/kotlin/NullableQuantity.kt");
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
            return Scenario.Kotlin.of("Ids.kt with required IdGenerator.kt",
                    "scenarios/kotlin/Ids.kt",
                    "util/IdGenerator.java");
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
            return Scenario.Kotlin.ofSingle("scenarios/kotlin/OrderDate.kt");
        }

        /**
         * All predefined sample scenarios, without any dependencies.
         * @return immutable list of all predefined scenarios
         * @see #isbn()
         * @see #quantity()
         * @see #nullableQuantity()
         * @see #orderDate()
         * @see #ids()
         */
        public static List<ScenarioFactory<Kotlin>> all() {
            return List.of(isbn(), quantity(), nullableQuantity(), orderDate(), ids());
        }

        /**
         * Combines all sources into a single scenario.
         * @return new ScenarioFactory instance
         * @see #isbn()
         * @see #quantity()
         * @see #nullableQuantity()
         * @see #orderDate()
         * @see #ids()
         */
        public static ScenarioFactory<Kotlin> combined() {
            return Scenario.Kotlin.of("All predefined sources in one compilation",
                    "scenarios/kotlin/Isbn.kt",
                    "scenarios/kotlin/Quantity.kt",
                    "scenarios/kotlin/NullableQuantity.kt",
                    "scenarios/kotlin/OrderDate.kt",
                    "scenarios/kotlin/Ids.kt"
            );
        }
    }


    /**
     * Describes required and optional information for a scenario.
     * @param name the name describing this scenario
     * @param sources all source files relevant for the compilation (imports). Must not contain null elements.
     * @param dependencies will be added to the classpath of the temporary project. Optional but must not contain null elements.
     * @param options will be added as a processor-option "lazyval.generators.disable" to the compilation. Optional but must not contain null elements.
     */
    record Descriptor(
            String name,
            ImmutableCollection<File> sources,
            ImmutableCollection<Dependency> dependencies,
            ImmutableMap<String, String> options){

        /**
         * Creates a new Descriptor instance.
         * @param sources all source files relevant for the compilation (imports). Must not contain null elements.
         * @param dependencies will be added to the classpath of the temporary project. Can be empty (optional) but must not contain null elements.
         * @param options will be added as a processor-option. Can be empty (optional).
         */
        public Descriptor {
            Objects.requireNonNull(name);
            Objects.requireNonNull(sources);
            Objects.requireNonNull(dependencies);
            for (File f : sources) {
                Objects.requireNonNull(f, "sources must not contain null elements");
            }
            for (Dependency d : dependencies) {
                Objects.requireNonNull(d, "dependencies must not contain null elements");
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
