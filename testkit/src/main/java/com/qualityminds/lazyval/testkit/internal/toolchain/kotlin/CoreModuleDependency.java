package com.qualityminds.lazyval.testkit.internal.toolchain.kotlin;

import com.qualityminds.lazyval.collections.NonEmptySet;
import com.qualityminds.lazyval.testkit.dependencies.Dependency;
import com.qualityminds.lazyval.testkit.dependencies.InternalModuleDependency;
import com.qualityminds.lazyval.testkit.internal.Versions;

import java.io.File;

// Duplicated to keep package-private
class CoreModuleDependency {
    static final NonEmptySet<File> RESOLVED_FILE;
    static {
        RESOLVED_FILE = new InternalModuleDependency("../core", new Dependency("com.qualityminds.lazyval", "lazyval", Versions.LAZYVAL_BUILD_VERSION)).resolve();
    }
}
