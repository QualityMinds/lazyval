package com.qualityminds.lazyval.testkit.internal;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Reads classpath resources uniformly regardless of whether they sit on the filesystem (loose under
 * {@code target/test-classes}, typical during a Maven build or in the IDE) or inside a JAR (anyone
 * consuming the testkit as a packaged dependency, or running tests from a shaded uber-jar).
 * <p>
 * Internally uses {@link ClassLoader#getResourceAsStream(String)} rather than {@code getResource}
 * followed by {@code Path.of(uri)}, because the latter fails for {@code jar:} URIs — those don't map
 * to a real filesystem path and would need a {@link java.nio.file.FileSystem} lookup. The
 * stream-based API hides that distinction.
 */
public final class ClasspathResources {

    private ClasspathResources() {}

    /**
     * Reads the resource at {@code resourcePath} from the current thread's context classloader and
     * returns its bytes.
     *
     * @param resourcePath classpath-relative path, e.g. {@code "approvals/jpa/X.java"}
     * @return the resource's content as bytes
     * @throws IllegalArgumentException if no resource exists at that path on the classpath
     * @throws UncheckedIOException     if the resource exists but reading it failed
     */
    public static byte[] readBytes(String resourcePath) {
        var classloader = Thread.currentThread().getContextClassLoader();
        try (var input = classloader.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalArgumentException("Resource not found on classpath: " + resourcePath);
            }
            return input.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read resource: " + resourcePath, e);
        }
    }
}
