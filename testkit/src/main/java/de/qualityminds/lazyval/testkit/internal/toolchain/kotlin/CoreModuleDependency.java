package de.qualityminds.lazyval.testkit.internal.toolchain.kotlin;

import de.qualityminds.lazyval.collections.NonEmptySet;
import de.qualityminds.lazyval.testkit.dependencies.Dependency;
import de.qualityminds.lazyval.testkit.dependencies.InternalModuleDependency;
import de.qualityminds.lazyval.testkit.internal.Versions;

import java.io.File;

// Duplicated to keep package-private
class CoreModuleDependency {
    static final NonEmptySet<File> RESOLVED_FILE;
    static {
        RESOLVED_FILE = new InternalModuleDependency("../core", new Dependency("de.qualityminds.lazyval", "lazyval", Versions.LAZYVAL_BUILD_VERSION)).resolve();
    }
}
