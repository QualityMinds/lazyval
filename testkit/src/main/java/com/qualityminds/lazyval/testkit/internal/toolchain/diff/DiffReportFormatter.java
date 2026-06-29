package com.qualityminds.lazyval.testkit.internal.toolchain.diff;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a {@link ReportModel} into a stacked, unified-style diff report.
 * <p>
 * The layout matches a git-style hunk view: changed lines are prefixed with {@code +} (added) or
 * {@code -} (removed); a few unchanged context lines surround each change; non-adjacent hunks are
 * separated by {@code @@}. Within a {@code CHANGE} pair, the exact changed sub-string is highlighted
 * in a brighter color (ANSI mode) or bracketed with {@code [...]} (plain mode).
 */
final class DiffReportFormatter {

    // line-level (foreground) colors
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String DIM = "\u001B[2m";
    // inline (sub-line) highlight: brighter foreground so the exact changed segment stands out
    private static final String DELETE_HL = "[91m"; // bright red text
    private static final String INSERT_HL = "[92m"; // bright green text

    private DiffReportFormatter() {
    }

    static String format(ReportModel model, RenderOptions opts) {
        if (model.isEmpty()) {
            return "";
        }
        var rows = model.rows();
        boolean[] keep = selectRowsWithinContext(rows, opts.context());

        var report = new ArrayList<String>();
        boolean previousKept = false;
        for (int i = 0; i < rows.size(); i++) {
            if (!keep[i]) {
                previousKept = false;
                continue;
            }
            if (!previousKept && !report.isEmpty()) {
                report.add(color("@@", DIM, opts.ansi()));
            }
            report.addAll(formatRow(rows.get(i), opts));
            previousKept = true;
        }
        return String.join(System.lineSeparator(), report);
    }

    /**
     * Marks each row that should appear in the report: every changed row, plus up to {@code context}
     * unchanged rows on either side of a change.
     */
    private static boolean[] selectRowsWithinContext(List<ReportRow> rows, int context) {
        boolean[] keep = new boolean[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).tag() != ReportRow.Tag.EQUAL) {
                int from = Math.max(0, i - context);
                int to = Math.min(rows.size() - 1, i + context);
                for (int j = from; j <= to; j++) {
                    keep[j] = true;
                }
            }
        }
        return keep;
    }

    private static List<String> formatRow(ReportRow row, RenderOptions opts) {
        boolean ansi = opts.ansi();
        return switch (row.tag()) {
            case EQUAL -> List.of("  " + plain(row.oldSegments()));
            case INSERT -> List.of(color("+ " + plain(row.newSegments()), GREEN, ansi));
            case DELETE -> List.of(color("- " + plain(row.oldSegments()), RED, ansi));
            case CHANGE -> List.of(
                    color("- " + inline(row.oldSegments(), DELETE_HL, ansi), RED, ansi),
                    color("+ " + inline(row.newSegments(), INSERT_HL, ansi), GREEN, ansi));
        };
    }

    private static String plain(List<ReportRow.Segment> segments) {
        var sb = new StringBuilder();
        for (var s : segments) {
            sb.append(s.text());
        }
        return sb.toString();
    }

    /**
     * Joins the segments, decorating highlighted runs: ANSI escape codes when {@code ansi}, otherwise
     * {@code [...]} brackets that survive plain-text rendering.
     */
    private static String inline(List<ReportRow.Segment> segments, String ansiCode, boolean ansi) {
        var sb = new StringBuilder();
        for (var s : segments) {
            if (s.highlighted()) {
                if (ansi) {
                    sb.append(ansiCode).append(s.text()).append(RESET);
                } else {
                    sb.append('[').append(s.text()).append(']');
                }
            } else {
                sb.append(s.text());
            }
        }
        return sb.toString();
    }

    private static String color(String line, String code, boolean ansi) {
        return ansi ? code + line + RESET : line;
    }
}
