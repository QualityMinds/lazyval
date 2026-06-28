package com.qualityminds.lazyval.testkit.internal.toolchain.diff;

import java.util.List;

/**
 * Structured, render-free representation of a diff: the ordered row stream produced by the engine,
 * including unchanged rows that serve as context. Rendering options (ANSI, context width) are applied
 * by {@link DiffReportFormatter} when the report is actually emitted.
 */
record ReportModel(List<ReportRow> rows) {

    static final ReportModel EMPTY = new ReportModel(List.of());

    ReportModel(List<ReportRow> rows) {
        this.rows = List.copyOf(rows);
    }

    static ReportModel empty() {
        return EMPTY;
    }

    boolean isEmpty() {
        return rows.isEmpty();
    }
}
