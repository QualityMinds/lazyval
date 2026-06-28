package com.qualityminds.lazyval.testkit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 *       whitespace and blank-line differences are tolerated by
 *       {@link com.qualityminds.lazyval.testkit.internal.toolchain.diff.Diff}.</li>
 * </ul>
 */
public sealed interface Approval {

    /**
     * Slash-separated path of the generated artifact, relative to the toolchain's output root for
     * its kind. See the variant javadoc for which root that is.
     */
    String generatedPath();

    /** Full expected text of the generated artifact. */
    String expectedContent();

    /**
     * Loads a classpath resource as UTF-8 text. Used by every {@code at(...)} factory; extracted
     * here to keep variant factories small.
     */
    private static String loadResource(String resourcePath) {
        var url = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
        if (url == null) {
            throw new IllegalArgumentException("Approval resource not found on classpath: " + resourcePath);
        }
        try {
            // Using toURI() (not getPath()) so paths containing spaces or other URL-encoded
            // characters resolve correctly on every platform.
            return Files.readString(Path.of(url.toURI()));
        } catch (URISyntaxException | IOException e) {
            throw new UncheckedIOException("Failed to read approval resource: " + resourcePath,
                    e instanceof IOException io ? io : new IOException(e));
        }
    }

    /**
     * Generated Java source. Java APT emits these under {@code SOURCE_OUTPUT}
     * ({@code build/generated/}); KSP can also emit Java sources, under
     * {@code build/generated/ksp/java/}.
     * <p>
     * Example: {@code "test/boundary/persistence/jpa/QuantityAttributeConverter.java"}.
     */
    record JavaSource(String generatedPath, String expectedContent) implements Approval {

        public JavaSource {
            Objects.requireNonNull(generatedPath, "generatedPath must not be null");
            Objects.requireNonNull(expectedContent, "expectedContent must not be null");
        }

        /** Literal-content factory. */
        public static JavaSource of(String generatedPath, String expectedContent) {
            return new JavaSource(generatedPath, expectedContent);
        }

        /** Resource-backed factory; reads {@code resourcePath} from the test classpath. */
        public static JavaSource at(String generatedPath, String resourcePath) {
            return new JavaSource(generatedPath, loadResource(resourcePath));
        }
    }

    /**
     * Generated Kotlin source — KSP only. Lands under {@code build/generated/ksp/kotlin/}.
     * <p>
     * Passing a {@code KotlinSource} to {@link Testkit.Java#run(Path, com.qualityminds.lazyval.testkit.scenarios.Scenario.Java, Approval...)}
     * throws {@link IllegalArgumentException} — the Java testkit has no Kotlin output to compare against.
     * <p>
     * Example: {@code "test/boundary/persistence/jpa/QuantityAttributeConverter.kt"}.
     */
    record KotlinSource(String generatedPath, String expectedContent) implements Approval {

        public KotlinSource {
            Objects.requireNonNull(generatedPath, "generatedPath must not be null");
            Objects.requireNonNull(expectedContent, "expectedContent must not be null");
        }

        /** Literal-content factory. */
        public static KotlinSource of(String generatedPath, String expectedContent) {
            return new KotlinSource(generatedPath, expectedContent);
        }

        /** Resource-backed factory; reads {@code resourcePath} from the test classpath. */
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
     */
    record Resource(String generatedPath, String expectedContent) implements Approval {

        public Resource {
            Objects.requireNonNull(generatedPath, "generatedPath must not be null");
            Objects.requireNonNull(expectedContent, "expectedContent must not be null");
        }

        /** Literal-content factory. */
        public static Resource of(String generatedPath, String expectedContent) {
            return new Resource(generatedPath, expectedContent);
        }

        /** Resource-backed factory; reads {@code resourcePath} from the test classpath. */
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
     */
    record ServiceLoader(String generatedPath, String expectedContent) implements Approval {

        private static final String PREFIX = "META-INF/services/";

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
         */
        public static ServiceLoader at(String interfaceFqn, String resourcePath) {
            Objects.requireNonNull(interfaceFqn, "interfaceFqn must not be null");
            return new ServiceLoader(PREFIX + interfaceFqn, loadResource(resourcePath));
        }
    }
}
