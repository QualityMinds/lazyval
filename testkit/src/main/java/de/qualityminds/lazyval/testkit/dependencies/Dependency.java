package de.qualityminds.lazyval.testkit.dependencies;

import de.qualityminds.lazyval.collections.NonEmptySet;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Represents a dependency used to dynamically set up a classpath for the processors during tests.
 * Uses Maven coordinates to identify the dependency.
 * <p>
 * The testkit uses an internal Maven resolver which checks the users' home directory for cached artifacts and
 * downloads them if necessary to the cache.
 * <p>
 * See <a href="https://github.com/qualityminds/lazyval/issues/12">limitations</a>.
 *
 * @param groupId the group id of the dependency, not null
 * @param artifactId the artifact id of the dependency, not null
 * @param version the version of the dependency, not null
 *
 */
public record Dependency(String groupId, String artifactId, String version) {

    private static final Function<Dependency, NonEmptySet<File>> mavenResolver = d -> MavenResolver
            .resolveDependencies(d.toCoordinates());

    private static final Map<Dependency, NonEmptySet<File>> cachedDependencies = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Creates a new dependency instance.
     * @param groupId the group id of the dependency, not null
     * @param artifactId the artifact id of the dependency, not null
     * @param version the version of the dependency, not null
     */
    public Dependency {
        Objects.requireNonNull(groupId);
        Objects.requireNonNull(artifactId);
        Objects.requireNonNull(version);
    }

    /**
     * Converts this dependency to a coordinate string in the format {@code <groupId>:<artifactId>:<version>}
     * @return coordinates as string
     */
    public String toCoordinates() {
        return groupId + ":" + artifactId + ":" + version;
    }

    /**
     * INTERNAL USE ONLY. Called by the Java and Kotlin toolchains to set up the classpath.
     * <p>
     * Resolves this dependency using the default Maven resolver. Resulting files will be cached.
     * @return the resolved files
     * @throws IllegalStateException if the dependency could not be resolved.
     * @see #resolve(Function)
     */
    // FIXME: check if instead of NonEmtpySet just the File should be returned.
    public NonEmptySet<File> resolve() {
        return resolve(mavenResolver);
    }

    /**
     * INTERNAL USE ONLY. Called by the Java and Kotlin toolchains to set up the classpath.
     * <p>
     * Resolves this dependency using the given resolver.
     * @param resolver the resolver used to resolve this dependency to an actual File
     * @return the resolved files
     */
    // FIXME: check if instead of NonEmtpySet just the File should be returned.
    public NonEmptySet<File> resolve(Function<Dependency, NonEmptySet<File>> resolver) {
        Objects.requireNonNull(resolver);
        var files = cachedDependencies.computeIfAbsent(this, d ->
                resolver.apply(this));
        files.forEach(f -> {
            if(!f.exists()){
                throw new IllegalStateException("Cached file does not exist anymore: " + f);
            }
        });
        return files;
    }
}
