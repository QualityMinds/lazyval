package com.qualityminds.lazyval.testkit.internal.toolchain.diff;

/**
 * Display-time options for rendering a {@link ComparisonResult.Mismatch}.
 * <p>
 * Kept separate from the comparison itself so that the same result can be rendered with or without
 * ANSI color depending on the consumer (terminal vs. log file vs. CI output).
 *
 * @param ansi    whether to emit ANSI escape codes for colors
 * @param context number of unchanged lines to keep around each change in the contextual report;
 *                must be {@code >= 0}
 */
public record RenderOptions(boolean ansi, int context) {

    private static final int DEFAULT_CONTEXT = 3;

    public RenderOptions {
        if (context < 0) {
            throw new IllegalArgumentException("context must be >= 0, was " + context);
        }
    }

    /** Plain text output, default context width. Safe for log files and CI. */
    public static RenderOptions plain() {
        return new RenderOptions(false, DEFAULT_CONTEXT);
    }

    /** ANSI-colored output, default context width. Intended for interactive terminals. */
    public static RenderOptions colored() {
        return new RenderOptions(true, DEFAULT_CONTEXT);
    }
}
