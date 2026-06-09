package com.qualityminds.lazyval.testkit.internal.toolchain.kotlin;

/**
 * Result of executing a single {@link Step}. Distinguishes legitimate non-execution ({@link #SKIPPED})
 * from real failures ({@link #COMPILE_ERROR}, {@link #INTERNAL_ERROR}) so that callers can debug
 * a failed run without consulting the underlying tool's native exit code.
 */
public enum StepOutcome {
    /** The step ran and reported success. */
    SUCCESS,
    /** The step had nothing to do (e.g. javac when KSP produced no Java sources). Treated as success. */
    SKIPPED,
    /** The step ran, and the tool reported a normal compiler error (e.g. unresolved symbol, type mismatch). */
    COMPILE_ERROR,
    /** The step ran, and the tool failed for an internal reason (out-of-memory, internal compiler error). */
    INTERNAL_ERROR;

    /** Whether this outcome counts as a successful run of the step. */
    public boolean isSuccessful() {
        return this == SUCCESS || this == SKIPPED;
    }
}
