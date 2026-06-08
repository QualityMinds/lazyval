package com.qualityminds.lazyval.testkit.internal.toolchain.kotlin;

import java.nio.file.Path;

/**
 * Filesystem layout of a Kotlin toolchain run, shared by the KSP, kotlinc and javac steps.
 * <p>
 * All accessors return paths relative to {@link #projectDir()}. They are pure — no directory is created
 * on disk. Callers that need a directory to exist must invoke {@link java.nio.file.Files#createDirectories}
 * themselves.
 *
 * @param projectDir the project root for this toolchain run; typically a JUnit-provided temp directory
 */
record ProjectLayout(Path projectDir) {

    /**
     * The build directory under which all generated and compiled artifacts live.
     */
    Path buildDir() {
        return projectDir.resolve("build");
    }

    /**
     * Output directory for compiled {@code .class} files. Used as the destination by both kotlinc and javac
     * so generated Java sources can resolve Kotlin types compiled in the preceding step. Also referenced
     * by KSP via {@code KSPJvmConfig.setClassOutputDir}.
     */
    Path classes() {
        return buildDir().resolve("classes");
    }

    /**
     * KSP writes generated {@code .java} sources here. Picked up by the javac step that runs after kotlinc.
     */
    Path kspJavaOutput() {
        return buildDir().resolve("generated/ksp/java");
    }

    /**
     * KSP writes generated {@code .kt} sources here. Picked up by the kotlinc step.
     */
    Path kspKotlinOutput() {
        return buildDir().resolve("generated/ksp/kotlin");
    }

    /**
     * KSP writes generated resources (e.g. {@code META-INF/services}) here. The directory is added to the
     * kotlinc classpath so ServiceLoader discovery works during compilation.
     */
    Path kspResourceOutput() {
        return buildDir().resolve("generated/ksp/resources");
    }

    /**
     * Cache directory for KSP's internal incremental state, passed to {@code KSPJvmConfig.setCachesDir}.
     * <p>
     * Despite living under {@code build/resources}, this is NOT a resource-output directory and is unrelated
     * to {@link #kspResourceOutput()}. The historic name comes from {@code KSPJvmConfig} defaults.
     */
    Path kspCachesDir() {
        return buildDir().resolve("resources");
    }
}
