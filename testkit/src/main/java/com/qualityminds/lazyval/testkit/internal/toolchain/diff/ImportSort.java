package com.qualityminds.lazyval.testkit.internal.toolchain.diff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Sorts the top-of-file {@code import} block in a Java or Kotlin source string so purely cosmetic
 * import reorderings — generator-version drift, IDE auto-format on a hand-edited fixture — collapse
 * to a match in the diff.
 * <p>
 * <strong>What counts as an import line:</strong> the line must start at column 0 with the literal
 * keyword {@code import} followed by at least one whitespace character. This deliberately excludes
 * indented lines, the substring {@code "import"} appearing as data inside a string or comment, and
 * tokens such as {@code importsomething} that share the prefix but not the grammar.
 * <p>
 * <strong>Block extraction:</strong> walking from the top of file, the block starts at the first
 * import line and continues across blank lines until the first non-blank, non-import line (which
 * includes any comment — block-enders by design, so a section-divider comment inside imports won't
 * cause the sorter to swallow the next declaration's javadoc). Anything before the first import is
 * preserved verbatim, including package declarations, file-level comments, and license headers.
 * <p>
 * <strong>Sort key:</strong> the raw line text under {@link Comparator#naturalOrder()}. Static
 * imports interleave with non-static imports by string order rather than by any style convention;
 * this is purely cosmetic for diffing since both sides are sorted identically.
 * <p>
 * <strong>Blank lines within the block</strong> are dropped in the sorted output. The diff's
 * existing blank-line tolerance absorbs any spacing mismatch around the block.
 * <p>
 * <strong>Limitations:</strong> only the first import block is sorted (Java grammar permits only
 * one anyway; a generator that emits more is producing invalid code). And when the sorted blocks
 * differ between sides, the resulting {@link ComparisonResult.Difference#lineNumber()} values refer
 * to positions in the sorted sequence rather than to the original source line — the line
 * <em>text</em> is still meaningful for navigation. This is a robustness layer, not a substitute
 * for keeping fixtures in sync with the generator.
 */
public final class ImportSort {

    private ImportSort() {}

    /**
     * Returns {@code content} with its top-of-file import block sorted lexicographically.
     * If no import block is present, returns {@code content} unchanged (same reference).
     */
    public static String sort(String content) {
        var lines = Arrays.asList(content.split("\n", -1));
        var sorted = sortLines(lines);
        if (sorted == lines) {
            return content;
        }
        return String.join("\n", sorted);
    }

    /**
     * Same as {@link #sort(String)} but operating on a pre-split line list. Returns the input
     * reference unchanged when no import block is found, so callers can short-circuit cheaply.
     */
    static List<String> sortLines(List<String> lines) {
        int firstImport = -1;
        int lastImport = -1;
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            if (isImportLine(line)) {
                if (firstImport < 0) firstImport = i;
                lastImport = i;
            } else if (firstImport >= 0 && !line.isBlank()) {
                break;
            }
        }
        if (firstImport < 0) {
            return lines;
        }

        var imports = new ArrayList<String>(lastImport - firstImport + 1);
        for (int i = firstImport; i <= lastImport; i++) {
            var line = lines.get(i);
            if (isImportLine(line)) {
                imports.add(line);
            }
        }
        imports.sort(Comparator.naturalOrder());

        var out = new ArrayList<String>(lines.size() - (lastImport - firstImport + 1) + imports.size());
        out.addAll(lines.subList(0, firstImport));
        out.addAll(imports);
        out.addAll(lines.subList(lastImport + 1, lines.size()));
        return out;
    }

    private static boolean isImportLine(String line) {
        return line.length() > 7
                && line.startsWith("import")
                && Character.isWhitespace(line.charAt(6));
    }
}
