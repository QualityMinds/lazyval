package de.qualityminds.lazyval.testkit.dependencies;


import de.qualityminds.lazyval.collections.NonEmptySet;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * INTERNAL USE ONLY!
 * <p>
 * This Dependency is needed to resolve dependencies to the compiled classes of other modules in this tree
 * (namely the two processors). This is needed because when executing integration-tests which need access to
 * the latest compiled classes which are not yet available in the local repo.
 * </p>
 */
public record InternalModuleDependency(String relativeModulePath) implements ClasspathDependency<InternalModuleDependency> {

    private static final Function<InternalModuleDependency, NonEmptySet<File>> mavenResolver = d -> MavenResolver
            .getModuleClasses(d.relativeModulePath);

    private static final Map<InternalModuleDependency, NonEmptySet<File>> cachedDependencies = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public NonEmptySet<File> resolve() {
        return resolve(mavenResolver);
    }

    @Override
    public NonEmptySet<File> resolve(Function<InternalModuleDependency, NonEmptySet<File>> resolver) {
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
