package com.qualityminds.lazyval.testkit.internal.toolchain.kotlin;

/**
 * Identifies one of the steps in the Kotlin toolchain pipeline. The order in which they execute is
 * {@link #KSP} → {@link #KOTLINC} → {@link #JAVAC}; later steps depend on earlier ones succeeding.
 */
public enum Step {
    /** Kotlin Symbol Processing — generates {@code .kt}, {@code .java} and resource files from Kotlin sources. */
    KSP,
    /** Kotlin compiler — compiles original and generated {@code .kt} sources to {@code .class} files. */
    KOTLINC,
    /** Java compiler — compiles generated {@code .java} sources to {@code .class} files. */
    JAVAC
}
