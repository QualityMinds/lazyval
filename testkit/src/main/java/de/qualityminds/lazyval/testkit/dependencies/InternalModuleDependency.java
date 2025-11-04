package de.qualityminds.lazyval.testkit.dependencies;


import de.qualityminds.lazyval.collections.NonEmptySet;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * INTERNAL USE ONLY!
 * <p>
 * This Dependency is needed to resolve dependencies to the compiled classes of other modules in this tree.
 * This is needed because when executing integration-tests which need access to the latest compiled classes which
 * are not yet available in the local repo.
 * </p>
 */
public record InternalModuleDependency(String relativeModulePath, Dependency fallback) {

    private static final Function<InternalModuleDependency, NonEmptySet<File>> mavenResolver = d -> {
        try {
            return MavenResolver.getModuleClasses(d.relativeModulePath);
        }catch(RuntimeException ignored){
            return MavenResolver.resolveDependencies(d.fallback.toCoordinates());
        }
    };

    private static final Map<InternalModuleDependency, NonEmptySet<File>> cachedDependencies = new java.util.concurrent.ConcurrentHashMap<>();

    public NonEmptySet<File> resolve() {
        return resolve(mavenResolver);
    }

    public NonEmptySet<File> resolve(Function<InternalModuleDependency, NonEmptySet<File>> resolver) {
        Objects.requireNonNull(resolver);
        var files = cachedDependencies.computeIfAbsent(this, d -> {
            try{
                return resolver.apply(this);
            }catch(Exception e){
                return fallback.resolve();
            }
        });
        files.forEach(f -> {
            if(!f.exists()){
                throw new IllegalStateException("Cached file does not exist anymore: " + f);
            }
        });
        return files;
    }
}
