package com.qualityminds.lazyval.testkit.internal.toolchain.diff;

import java.util.List;

/**
 * A single row in the {@link ReportModel structured report}. For {@link Tag#CHANGE CHANGE} rows,
 * each side carries the inline word-level segmentation so a renderer can highlight the exact changed
 * sub-string. For other tags the line text lives in a single non-highlighted segment on the relevant
 * side.
 */
record ReportRow(Tag tag, List<Segment> oldSegments, List<Segment> newSegments) {

    enum Tag {EQUAL, UNEXPECTED, MISSING, CHANGE}

    /** A contiguous run of text within a line, optionally marked as inline-changed. */
    record Segment(String text, boolean highlighted) {}

    ReportRow {
        oldSegments = List.copyOf(oldSegments);
        newSegments = List.copyOf(newSegments);
    }

    static ReportRow equal(String text) {
        return new ReportRow(Tag.EQUAL, List.of(new Segment(text, false)), List.of());
    }

    static ReportRow removed(String text) {
        return new ReportRow(Tag.MISSING, List.of(new Segment(text, false)), List.of());
    }

    static ReportRow added(String text) {
        return new ReportRow(Tag.UNEXPECTED, List.of(), List.of(new Segment(text, false)));
    }

    static ReportRow changed(List<Segment> oldSegments, List<Segment> newSegments) {
        return new ReportRow(Tag.CHANGE, oldSegments, newSegments);
    }
}
