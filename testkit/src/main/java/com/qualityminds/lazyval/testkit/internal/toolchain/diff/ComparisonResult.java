package com.qualityminds.lazyval.testkit.internal.toolchain.diff;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public sealed interface ComparisonResult {

    record Match() implements ComparisonResult {
        private static final Match INSTANCE = new Match();
    }

    /**
     * A mismatch between actual and expected content.
     * <p>
     * Equality is defined solely by {@link #changes() the structured changes}. To keep equality and
     * display consistent, {@link #toString()} is derived from those same changes, so two equal mismatches
     * always render identically (which makes side-by-side comparison in IDEs/test runners meaningful).
     * <p>
     * The colored, context-aware report is intentionally <em>not</em> part of {@code toString} (and not part
     * of {@code equals}/{@code hashCode}); it is exposed separately via {@link #render(RenderOptions)} so
     * the caller controls ANSI, context width, and the like at display time.
     */
    final class Mismatch implements ComparisonResult {

        private final List<Difference> differences;
        private final ReportModel report;

        Mismatch(List<Difference> differences, ReportModel report) {
            this.differences = List.copyOf(differences);
            this.report = Objects.requireNonNull(report);
        }

        /** Convenience for building expectations in tests; no contextual report attached. */
        public static Mismatch of(Difference... differences) {
            return new Mismatch(List.of(differences), ReportModel.empty());
        }

        public List<Difference> changes() {
            return differences;
        }

        /**
         * Renders the rich, context-aware diff report intended for human consumption: changed lines with
         * {@code +}/{@code -} prefixes, a configurable window of unchanged context lines around each change,
         * {@code @@} separators between non-adjacent hunks, and inline word-level highlighting of the changed
         * sub-string within paired changed lines.
         * <p>
         * For mismatches built via {@link #of(Difference...)} this returns an empty string — the report
         * requires data only the comparison engine can produce.
         *
         * @param options ANSI, context width, etc. See {@link RenderOptions}.
         * @return the rendered report, or an empty string when no report is attached
         */
        public String render(RenderOptions options) {
            return DiffReportFormatter.format(report, options);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Mismatch other && differences.equals(other.differences);
        }

        @Override
        public int hashCode() {
            return differences.hashCode();
        }

        @Override
        public String toString() {
            return differences.stream()
                    .map(Difference::toString)
                    .collect(Collectors.joining(System.lineSeparator()));
        }
    }

    static Match match() {
        return Match.INSTANCE;
    }

    /**
     * A single changed line. Line numbers are 1-based and refer to the side on which the line exists:
     * for {@link Kind#PRESENT PRESENT} differences the line exists in {@code actual}; for
     * {@link Kind#MISSING MISSING} differences the line exists in {@code expected}.
     */
    record Difference(Kind kind, int lineNumber, String text) {

        public enum Kind {
            /** A line present in actual but not in expected — i.e., added. */
            PRESENT,
            /** A line present in expected but not in actual — i.e., removed. */
            MISSING
        }

        public static Difference present(int lineNumber, String text) {
            return new Difference(Kind.PRESENT, lineNumber, text);
        }

        public static Difference missing(int lineNumber, String text) {
            return new Difference(Kind.MISSING, lineNumber, text);
        }

        @Override
        public String toString() {
            char sign = kind == Kind.PRESENT ? '+' : '-';
            return "%c L%d: %s".formatted(sign, lineNumber, text);
        }
    }
}
