package com.qualityminds.lazyval.testkit.internal.toolchain.diff;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a {@link ReportModel} into a stacked, unified-style diff report.
 * <p>
 * The layout matches a git-style hunk view: changed lines are prefixed with {@code +} (added) or
 * {@code -} (removed); a few unchanged context lines surround each change; non-adjacent hunks are
 * separated by {@code @@}. Within a {@code CHANGE} pair, the exact changed sub-string is bracketed
 * with {@code [...]} so it survives plain-text rendering everywhere the diff might land (CI logs,
 * IDE test runners, surefire reports).
 */
final class DiffReportFormatter {

    private static final int CONTEXT = 3;

    private DiffReportFormatter() {
    }

    static String format(ReportModel model) {
        if (model.isEmpty()) {
            return "";
        }
        var rows = model.rows();
        boolean[] keep = selectRowsWithinContext(rows, CONTEXT);

        var report = new ArrayList<String>();
        boolean previousKept = false;
        for (int i = 0; i < rows.size(); i++) {
            if (!keep[i]) {
                previousKept = false;
                continue;
            }
            if (!previousKept && !report.isEmpty()) {
                report.add("@@");
            }
            report.addAll(formatRow(rows.get(i)));
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

    private static List<String> formatRow(ReportRow row) {
        return switch (row.tag()) {
            case EQUAL -> List.of("  " + plain(row.oldSegments()));
            case UNEXPECTED -> List.of("+ " + plain(row.newSegments()));
            case MISSING -> List.of("- " + plain(row.oldSegments()));
            case CHANGE -> List.of(
                    "- " + inline(row.oldSegments()),
                    "+ " + inline(row.newSegments()));
        };
    }

    private static String plain(List<ReportRow.Segment> segments) {
        var sb = new StringBuilder();
        for (var s : segments) {
            sb.append(s.text());
        }
        return sb.toString();
    }

    /** Joins segments, bracketing highlighted runs with {@code [...]} so the changed sub-string stands out. */
    private static String inline(List<ReportRow.Segment> segments) {
        var sb = new StringBuilder();
        for (var s : segments) {
            if (s.highlighted()) {
                sb.append('[').append(s.text()).append(']');
            } else {
                sb.append(s.text());
            }
        }
        return sb.toString();
    }
}
