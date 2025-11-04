package de.qualityminds.lazyval.testkit.dependencies;

import de.qualityminds.lazyval.collections.NonEmptySet;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Represents a Maven dependency used to dynamically set up a classpath for the processors during tests
 */
public record Dependency(String groupId, String artifactId, String version) {

    private static final Function<Dependency, NonEmptySet<File>> mavenResolver = d -> MavenResolver
            .resolveDependencies(d.toCoordinates());

    private static final Map<Dependency, NonEmptySet<File>> cachedDependencies = new java.util.concurrent.ConcurrentHashMap<>();

    public Dependency {
        Objects.requireNonNull(groupId);
        Objects.requireNonNull(artifactId);
        Objects.requireNonNull(version);
    }

    public String toCoordinates() {
        return groupId + ":" + artifactId + ":" + version;
    }

    /**
     * Resolves this dependency using the default Maven resolver. Resulting files will be cached.
     * @return the resolved files
     * @throws IllegalStateException if the dependency could not be resolved.
     * @see #resolve(Function)
     */
    public NonEmptySet<File> resolve() {
        return resolve(mavenResolver);
    }

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
