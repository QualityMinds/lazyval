package com.qualityminds.lazyval.testkit.internal.toolchain.diff;

import com.github.difflib.text.DiffRowGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Compares generated content against expected content and produces a {@link ComparisonResult}.
 * <p>
 * Comparison is whitespace-insensitive at the line level (runs of whitespace are treated as equal)
 * and tabs are normalized to four spaces before comparing. Blank lines (empty or whitespace-only)
 * appearing on only one side are likewise ignored — they don't count as differences and don't appear
 * in the rendered report. Line numbers reported on real differences refer to the original input and
 * remain stable across such suppressed blanks, so failure messages stay navigable in the source.
 * <p>
 * The result carries the structured set of line-level differences used for equality; a
 * context-aware report is available on demand via {@link ComparisonResult.Mismatch#render()} —
 * this class is purely about the comparison and stays free of display concerns.
 */
public final class Diff {

    // Sentinel markers used to delimit inline-diff segments inside changed lines. Chosen to be control
    // characters that are extremely unlikely to occur in approval content; they are stripped or
    // converted to display markup by the renderer.
    private static final char SEGMENT_OPEN = (char) 0x01;
    private static final char SEGMENT_CLOSE = (char) 0x02;
    private static final String SEGMENT_OPEN_TAG = String.valueOf(SEGMENT_OPEN);
    private static final String SEGMENT_CLOSE_TAG = String.valueOf(SEGMENT_CLOSE);

    // Tabs render very differently across viewers; normalize to a stable 4-space width before
    // comparing so visually equal content compares equal regardless of indentation style.
    private static final UnaryOperator<String> NORMALIZER = line -> line.replace("\t", "    ");

    private Diff() {
    }

    /**
     * Compares the generated content against the expected content.
     *
     * @param actual   the generated content
     * @param expected the expected content to compare against
     * @return {@link ComparisonResult.Match} if equal, otherwise a {@link ComparisonResult.Mismatch}
     *         carrying the structured changes (for equality) and the data needed to render a
     *         contextual report on demand (for display)
     */
    public static ComparisonResult compare(String actual, String expected) {
        // Normalize line endings before splitting so CRLF (Windows checkouts of approval fixtures
        // via git's autocrlf, or generators using System.lineSeparator()) compares equal to LF.
        // The split below is \n-only, and we don't want the trailing \r per line to depend on the
        // diff library's ignoreWhiteSpaces heuristic.
        var actualLines = Arrays.asList(normalizeLineEndings(actual).split("\n", -1));
        var expectedLines = Arrays.asList(normalizeLineEndings(expected).split("\n", -1));
        var rows = generator().generateDiffRows(expectedLines, actualLines);

        var changes = new ArrayList<ComparisonResult.Difference>();
        var reportRows = new ArrayList<ReportRow>();
        int oldLine = 0;
        int newLine = 0;
        for (var row : rows) {
            switch (row.getTag()) {
                case EQUAL -> {
                    oldLine++;
                    newLine++;
                    reportRows.add(ReportRow.equal(stripMarkers(row.getOldLine())));
                }
                case DELETE -> {
                    oldLine++;
                    var text = stripMarkers(row.getOldLine());
                    // Always advance oldLine so subsequent real differences carry the original
                    // expected-side line number; only suppress emitting the change/report for blanks.
                    if (!text.isBlank()) {
                        changes.add(ComparisonResult.Difference.missing(oldLine, text));
                        reportRows.add(ReportRow.removed(text));
                    }
                }
                case INSERT -> {
                    newLine++;
                    var text = stripMarkers(row.getNewLine());
                    if (!text.isBlank()) {
                        changes.add(ComparisonResult.Difference.present(newLine, text));
                        reportRows.add(ReportRow.added(text));
                    }
                }
                case CHANGE -> {
                    oldLine++;
                    newLine++;
                    var oldSegments = toSegments(row.getOldLine());
                    var newSegments = toSegments(row.getNewLine());
                    var oldText = joinText(oldSegments);
                    var newText = joinText(newSegments);
                    // The engine may pair a blank on one side with content on the other as a CHANGE.
                    // Semantically that's not a modification but a pure addition or removal — treat it
                    // like the corresponding INSERT/DELETE, including the blank-line suppression rule.
                    boolean oldBlank = oldText.isBlank();
                    boolean newBlank = newText.isBlank();
                    //noinspection StatementWithEmptyBody
                    if (oldBlank && newBlank) {
                        // both sides cosmetic — nothing to report
                    } else if (oldBlank) {
                        changes.add(ComparisonResult.Difference.present(newLine, newText));
                        reportRows.add(ReportRow.added(newText));
                    } else if (newBlank) {
                        changes.add(ComparisonResult.Difference.missing(oldLine, oldText));
                        reportRows.add(ReportRow.removed(oldText));
                    } else {
                        changes.add(ComparisonResult.Difference.missing(oldLine, oldText));
                        changes.add(ComparisonResult.Difference.present(newLine, newText));
                        reportRows.add(ReportRow.changed(oldSegments, newSegments));
                    }
                }
            }
        }
        if (changes.isEmpty()) {
            return ComparisonResult.match();
        }
        return new ComparisonResult.Mismatch(changes, new ReportModel(reportRows));
    }

    private static String normalizeLineEndings(String text) {
        // Handle CRLF first to avoid leaving a stray \n from a CR-only pass.
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    private static DiffRowGenerator generator() {
        return DiffRowGenerator.create()
                .showInlineDiffs(true)
                .inlineDiffByWord(true)
                .ignoreWhiteSpaces(true)
                // the default normalizer HTML-escapes <, > and & (e.g. "<" -> "&lt;");
                // for plain-text/terminal output we want the raw characters preserved
                .lineNormalizer(NORMALIZER)
                // emit neutral sentinel markers around inline-diff segments; the renderer converts
                // them into [...] brackets at display time
                .oldTag(open -> open ? SEGMENT_OPEN_TAG : SEGMENT_CLOSE_TAG)
                .newTag(open -> open ? SEGMENT_OPEN_TAG : SEGMENT_CLOSE_TAG)
                .build();
    }

    /**
     * Splits a line carrying sentinel markers into ordered segments, each tagged as highlighted or not.
     * Lines without any markers degrade to a single non-highlighted segment.
     */
    private static List<ReportRow.Segment> toSegments(String text) {
        if (text.isEmpty()) {
            return List.of();
        }
        var segments = new ArrayList<ReportRow.Segment>();
        boolean highlighted = false;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == SEGMENT_OPEN || c == SEGMENT_CLOSE) {
                if (i > start) {
                    segments.add(new ReportRow.Segment(text.substring(start, i), highlighted));
                }
                highlighted = (c == SEGMENT_OPEN);
                start = i + 1;
            }
        }
        if (start < text.length()) {
            segments.add(new ReportRow.Segment(text.substring(start), highlighted));
        }
        return segments;
    }

    private static String stripMarkers(String text) {
        if (text.isEmpty()) {
            return "";
        }
        if (text.indexOf(SEGMENT_OPEN) < 0 && text.indexOf(SEGMENT_CLOSE) < 0) {
            return text;
        }
        var sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != SEGMENT_OPEN && c != SEGMENT_CLOSE) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String joinText(List<ReportRow.Segment> segments) {
        var sb = new StringBuilder();
        for (var s : segments) {
            sb.append(s.text());
        }
        return sb.toString();
    }
}
