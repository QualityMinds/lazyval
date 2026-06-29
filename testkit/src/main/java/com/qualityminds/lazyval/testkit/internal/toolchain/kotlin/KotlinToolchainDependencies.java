package com.qualityminds.lazyval.testkit.internal.toolchain.kotlin;

import com.qualityminds.lazyval.testkit.dependencies.Dependency;
import kotlin.KotlinVersion;

/**
 * Compile-classpath dependencies shared by the Kotlin toolchain steps. Centralized here so the
 * {@code (groupId, artifactId)} pairs don't drift between {@link KspStep}, {@link KotlinCompileStep}
 * and {@link JavaCompileStep}, each of which needs at least {@code kotlin-stdlib} on its classpath.
 */
final class KotlinToolchainDependencies {

    static final Dependency KOTLIN_STDLIB =
            new Dependency("org.jetbrains.kotlin", "kotlin-stdlib", KotlinVersion.CURRENT.toString());

    static final Dependency KOTLIN_REFLECT =
            new Dependency("org.jetbrains.kotlin", "kotlin-reflect", KotlinVersion.CURRENT.toString());

    private KotlinToolchainDependencies() {}
}
