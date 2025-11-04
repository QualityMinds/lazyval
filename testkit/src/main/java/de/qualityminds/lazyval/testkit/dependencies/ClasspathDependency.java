package de.qualityminds.lazyval.testkit.dependencies;


import de.qualityminds.lazyval.collections.NonEmptySet;

import java.io.File;
import java.util.function.Function;

/**
 * Used to set up the classpath for the compiler toolchains.
 */
public sealed interface ClasspathDependency<T extends ClasspathDependency<T>> permits InternalModuleDependency, Dependency {

    /**
     * Resolves this dependency using the default {@link Function}.
     * @return the resolved files
     * @throws IllegalStateException if the dependency could not be resolved, or a cached file does not exist anymore.
     * @see #resolve(Function)
     */
    NonEmptySet<File> resolve();

    /**
     * Resolves this dependency to a non-empty Set of Files usable by the toolchains. Resulting files will be cached.
     * @param resolver a function which is able to locate the dependency locally
     * @return the resolved files
     * @throws IllegalStateException if the dependency could not be resolved, or a cached file does not exist anymore.
     */
    NonEmptySet<File> resolve(Function<T, NonEmptySet<File>> resolver);
}
