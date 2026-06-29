package com.qualityminds.lazyval.testkit;

import com.qualityminds.lazyval.testkit.internal.ClasspathResources;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Pairs a generated artifact with the content it is expected to match.
 * <p>
 * The four variants reflect the kinds of output a toolchain can produce:
 * <ul>
 *   <li>{@link JavaSource} — Java source emitted by an annotation processor (APT or KSP).</li>
 *   <li>{@link KotlinSource} — Kotlin source emitted by KSP. Only valid in the Kotlin testkit;
 *       the Java testkit rejects it with {@link IllegalArgumentException}.</li>
 *   <li>{@link Resource} — non-source artifact written under the toolchain's resource output
 *       (e.g. a generated {@code .properties} file).</li>
 *   <li>{@link ServiceLoader} — convenience wrapper over {@link Resource} that derives the
 *       {@code META-INF/services/<fqn>} path from a service interface FQN.</li>
 * </ul>
 * <p>
 * Each variant shares two accessors:
 * <ul>
 *   <li>{@link #generatedPath()} — the slash-separated path of the file <em>relative to the
 *       toolchain's relevant output root</em>; the testkit uses this both for the file lookup and
 *       for diagnostic messages.</li>
 *   <li>{@link #expectedContent()} — the full text the generated file is expected to contain;
 *       the diff engine tolerates whitespace, blank-line, and line-ending differences, and sorts
 *       import blocks before comparing source files.</li>
 * </ul>
 */
public sealed interface Approval permits Approval.ForJava, Approval.ForKotlin {

    /**
     * Slash-separated path of the generated artifact, relative to the toolchain's output root for
     * its kind. See the variant javadoc for which root that is.
     * @return slash-separated path of the generated artifact
     */
    String generatedPath();

    /**
     * Full expected text of the generated artifact.
     * @return full expected text of the generated artifact
     */
    String expectedContent();

    /**
     * Marker for approvals the Java testkit can verify. {@link KotlinSource} is excluded because
     * javac cannot emit Kotlin output; everything else is in. Used in the parameter types of
     * {@link Testkit.Java} and {@link Testresult.Java.Approved#of} so the constraint is enforced at
     * compile time rather than via a runtime check.
     * <p>
     * Records implementing this marker are <em>also</em> {@link ForKotlin} where applicable — the
     * two markers form a Venn diagram rather than a partition, and each record's {@code implements}
     * clause states the testkits it is valid against.
     */
    sealed interface ForJava extends Approval permits JavaSource, Resource, ServiceLoader {}

    /**
     * Marker for approvals the Kotlin testkit can verify. Today every {@code Approval} variant is
     * valid against the Kotlin testkit (KSP can emit Java sources, Kotlin sources, and resources),
     * so this marker permits all four records. It is declared explicitly anyway to keep the type
     * signatures of {@link Testkit.Kotlin} and {@link Testresult.Kotlin.Approved#of} symmetric with
     * the Java side and forward-compatible: a future Kotlin-incompatible kind would simply be left
     * off the {@code permits} clause.
     */
    sealed interface ForKotlin extends Approval permits JavaSource, KotlinSource, Resource, ServiceLoader {}

    /**
     * Loads a classpath resource as UTF-8 text. Used by every {@code at(...)} factory; extracted
     * here to keep variant factories small. Handles loose-file and JAR-packaged fixtures uniformly.
     */
    private static String loadResource(String resourcePath) {
        return new String(ClasspathResources.readBytes(resourcePath), StandardCharsets.UTF_8);
    }

    /**
     * Generated Java source. Java APT emits these under {@code SOURCE_OUTPUT}
     * ({@code build/generated/}); KSP can also emit Java sources, under
     * {@code build/generated/ksp/java/}.
     * <p>
     * Example: {@code "test/boundary/persistence/jpa/QuantityAttributeConverter.java"}.
     *
     * @param generatedPath slash-separated path of the generated file, relative to the source-output root
     * @param expectedContent full expected text of the generated file
     */
    record JavaSource(String generatedPath, String expectedContent) implements ForJava, ForKotlin {

        /** Canonical constructor; rejects null components. */
        public JavaSource {
            Objects.requireNonNull(generatedPath, "generatedPath must not be null");
            Objects.requireNonNull(expectedContent, "expectedContent must not be null");
        }

        /**
         * Literal-content factory.
         * @param generatedPath slash-separated path of the generated file, relative to the source-output root
         * @param expectedContent full expected text of the generated file
         * @return a new JavaSource approval
         */
        public static JavaSource of(String generatedPath, String expectedContent) {
            return new JavaSource(generatedPath, expectedContent);
        }

        /**
         * Resource-backed factory; reads {@code resourcePath} from the test classpath.
         * @param generatedPath slash-separated path of the generated file, relative to the source-output root
         * @param resourcePath classpath-relative path to the approval fixture
         * @return a new JavaSource approval whose expected content is the fixture's text
         */
        public static JavaSource at(String generatedPath, String resourcePath) {
            return new JavaSource(generatedPath, loadResource(resourcePath));
        }
    }

    /**
     * Generated Kotlin source — KSP only. Lands under {@code build/generated/ksp/kotlin/}.
     * <p>
     * Unlike the other variants, {@code KotlinSource} implements only {@link ForKotlin} — it is
     * <em>not</em> a {@link ForJava}, so passing it to {@link Testkit.Java} or to
     * {@link Testresult.Java.Approved} is a compile-time error rather than a runtime check. The
     * Java testkit has no Kotlin output to compare against.
     * <p>
     * Example: {@code "test/boundary/persistence/jpa/QuantityAttributeConverter.kt"}.
     *
     * @param generatedPath slash-separated path of the generated file, relative to the source-output root
     * @param expectedContent full expected text of the generated file
     */
    record KotlinSource(String generatedPath, String expectedContent) implements ForKotlin {

        /** Canonical constructor; rejects null components. */
        public KotlinSource {
            Objects.requireNonNull(generatedPath, "generatedPath must not be null");
            Objects.requireNonNull(expectedContent, "expectedContent must not be null");
        }

        /**
         * Literal-content factory.
         * @param generatedPath slash-separated path of the generated file, relative to the source-output root
         * @param expectedContent full expected text of the generated file
         * @return a new KotlinSource approval
         */
        public static KotlinSource of(String generatedPath, String expectedContent) {
            return new KotlinSource(generatedPath, expectedContent);
        }

        /**
         * Resource-backed factory; reads {@code resourcePath} from the test classpath.
         * @param generatedPath slash-separated path of the generated file, relative to the source-output root
         * @param resourcePath classpath-relative path to the approval fixture
         * @return a new KotlinSource approval whose expected content is the fixture's text
         */
        public static KotlinSource at(String generatedPath, String resourcePath) {
            return new KotlinSource(generatedPath, loadResource(resourcePath));
        }
    }

    /**
     * Generated non-source artifact (properties file, JSON file, etc.). For APT-emitted resources
     * the path is relative to {@code CLASS_OUTPUT} ({@code build/classes/}); for KSP-emitted
     * resources it's relative to {@code build/generated/ksp/resources/}.
     * <p>
     * Example: {@code "META-INF/lazyval.properties"}.
     *
     * @param generatedPath slash-separated path of the generated file, relative to the resource-output root
     * @param expectedContent full expected text of the generated file
     */
    record Resource(String generatedPath, String expectedContent) implements ForJava, ForKotlin {

        /** Canonical constructor; rejects null components. */
        public Resource {
            Objects.requireNonNull(generatedPath, "generatedPath must not be null");
            Objects.requireNonNull(expectedContent, "expectedContent must not be null");
        }

        /**
         * Literal-content factory.
         * @param generatedPath slash-separated path of the generated file, relative to the resource-output root
         * @param expectedContent full expected text of the generated file
         * @return a new Resource approval
         */
        public static Resource of(String generatedPath, String expectedContent) {
            return new Resource(generatedPath, expectedContent);
        }

        /**
         * Resource-backed factory; reads {@code resourcePath} from the test classpath.
         * @param generatedPath slash-separated path of the generated file, relative to the resource-output root
         * @param resourcePath classpath-relative path to the approval fixture
         * @return a new Resource approval whose expected content is the fixture's text
         */
        public static Resource at(String generatedPath, String resourcePath) {
            return new Resource(generatedPath, loadResource(resourcePath));
        }
    }

    /**
     * ServiceLoader registration entry at {@code META-INF/services/<interfaceFqn>}. The factories
     * take the interface's fully-qualified name and derive the path internally, so callers don't
     * have to repeat the {@code META-INF/services/} prefix.
     * <p>
     * Underlying storage is the resolved {@link #generatedPath()}, identical to a {@link Resource}
     * at the same path; equality and lookup behave the same.
     *
     * @param generatedPath the resolved {@code META-INF/services/<interfaceFqn>} path
     * @param expectedContent full expected text of the services file (one provider FQN per line)
     */
    record ServiceLoader(String generatedPath, String expectedContent) implements ForJava, ForKotlin {

        private static final String PREFIX = "META-INF/services/";

        /** Canonical constructor; rejects null components. */
        public ServiceLoader {
            Objects.requireNonNull(generatedPath, "generatedPath must not be null");
            Objects.requireNonNull(expectedContent, "expectedContent must not be null");
        }

        /**
         * Literal-content factory; takes the service interface FQN, derives the META-INF path, and
         * joins {@code entries} with {@code \n} to form the expected file content. Each vararg is one
         * provider FQN line — pass them individually instead of pre-joining at the call site. A single
         * pre-joined string still works (varargs accepts one arg), so existing one-entry call sites
         * keep compiling unchanged.
         *
         * @param interfaceFqn fully-qualified name of the service interface; the {@code META-INF/services/}
         *                     prefix is appended internally
         * @param entries provider FQN lines, joined with {@code \n} to form the expected content
         * @return a new ServiceLoader approval
         */
        public static ServiceLoader of(String interfaceFqn, String... entries) {
            Objects.requireNonNull(interfaceFqn, "interfaceFqn must not be null");
            Objects.requireNonNull(entries, "entries must not be null");
            for (String entry : entries) {
                Objects.requireNonNull(entry, "entries must not contain null elements");
            }
            return new ServiceLoader(PREFIX + interfaceFqn, String.join("\n", entries));
        }

        /**
         * Resource-backed factory; the fixture at {@code resourcePath} is the expected content of
         * the services file.
         *
         * @param interfaceFqn fully-qualified name of the service interface; the {@code META-INF/services/}
         *                     prefix is appended internally
         * @param resourcePath classpath-relative path to the approval fixture
         * @return a new ServiceLoader approval whose expected content is the fixture's text
         */
        public static ServiceLoader at(String interfaceFqn, String resourcePath) {
            Objects.requireNonNull(interfaceFqn, "interfaceFqn must not be null");
            return new ServiceLoader(PREFIX + interfaceFqn, loadResource(resourcePath));
        }
    }
}
