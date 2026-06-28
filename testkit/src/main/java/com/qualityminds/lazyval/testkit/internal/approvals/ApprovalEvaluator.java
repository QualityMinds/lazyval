package com.qualityminds.lazyval.testkit.internal.approvals;

import com.qualityminds.lazyval.testkit.Approval;
import com.qualityminds.lazyval.testkit.internal.toolchain.diff.ComparisonResult;
import com.qualityminds.lazyval.testkit.internal.toolchain.diff.Diff;
import com.qualityminds.lazyval.testkit.internal.toolchain.diff.ImportSort;
import com.qualityminds.lazyval.testkit.internal.toolchain.diff.RenderOptions;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/**
 * Toolchain-agnostic core of the approval-testing flow.
 * <p>
 * Given a snapshot of files the toolchain produced (keyed by their slash-separated, relative-to-output-root
 * path) and a list of {@link Approval approval definitions}, returns an {@link Outcome} describing
 * whether the strict closed-set contract was satisfied: every approval matched and no extra files were
 * generated. The {@code Testkit} translates the toolchain-agnostic outcome into the public
 * {@code Testresult.{Java,Kotlin}.{Approved,ApprovalMismatch}} variant.
 * <p>
 * Pure function over the inputs aside from the file reads of the generated files themselves — which makes
 * this directly testable in the testkit module with files placed in a {@code @TempDir}, without any compiler
 * or annotation processor on the classpath.
 */
public final class ApprovalEvaluator {

    private ApprovalEvaluator() {}

    /**
     * Evaluates the given approvals against the snapshot of generated files.
     *
     * @param generatedByPath map of generated-file relative paths to their absolute filesystem locations;
     *                        must use forward-slash relative paths so it matches
     *                        {@link Approval#generatedPath()}
     * @param approvals       the approval definitions to verify (may be empty, though in that case the
     *                        testkit short-circuits to a different result type)
     * @return {@link Outcome.Approved} if every approval matched and no surplus files were generated,
     *         otherwise {@link Outcome.Mismatch} carrying one {@link Failure} per problem
     */
    public static Outcome evaluate(Map<String, Path> generatedByPath, Approval... approvals) {
        Objects.requireNonNull(generatedByPath, "generatedByPath must not be null");
        Objects.requireNonNull(approvals, "approvals must not be null");

        var allPaths = Lists.immutable.ofAll(generatedByPath.keySet()).toSortedList().toImmutable();
        var approvedPaths = new HashSet<String>();
        var failures = Lists.mutable.<Failure>empty();

        for (var approval : approvals) {
            approvedPaths.add(approval.generatedPath());
            var file = generatedByPath.get(approval.generatedPath());
            if (file == null) {
                failures.add(new Failure.FileNotFound(approval.generatedPath(), allPaths));
                continue;
            }
            var actual = readFile(file);
            var expected = approval.expectedContent();
            if (isSource(approval)) {
                // Source-only normalization: collapse import reorderings (generator version drift,
                // IDE auto-format on hand-edited fixtures) so they don't surface as diffs.
                actual = ImportSort.sort(actual);
                expected = ImportSort.sort(expected);
            }
            if (Diff.compare(actual, expected) instanceof ComparisonResult.Mismatch mismatch) {
                failures.add(new Failure.ContentDiffers(
                        approval.generatedPath(),
                        mismatch.render(RenderOptions.plain())));
            }
        }
        // Closed-set check: any file the run produced that no approval covers is a failure.
        for (var path : allPaths) {
            if (!approvedPaths.contains(path)) {
                failures.add(new Failure.UnexpectedFile(path));
            }
        }

        if (failures.isEmpty()) {
            return new Outcome.Approved(allPaths);
        }
        return new Outcome.Mismatch(failures.toImmutable());
    }

    private static boolean isSource(Approval approval) {
        return approval instanceof Approval.JavaSource
                || approval instanceof Approval.KotlinSource;
    }

    private static String readFile(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read generated file: " + file, e);
        }
    }

    /** The outcome of an evaluation. */
    public sealed interface Outcome {
        /**
         * Every approval matched its file and no surplus files were generated.
         *
         * @param generatedFiles complete enumeration of files the run produced (sorted)
         */
        record Approved(ImmutableList<String> generatedFiles) implements Outcome {}

        /**
         * At least one approval did not pass, or the run produced files outside the approved set.
         *
         * @param failures one entry per individual failure (a single run may produce several)
         */
        record Mismatch(ImmutableList<Failure> failures) implements Outcome {}
    }

    /** A single reason the strict approval contract was not satisfied. */
    public sealed interface Failure {
        /** The file existed at the expected path, but its content differed. */
        record ContentDiffers(String generatedPath, String renderedDiff) implements Failure {}
        /** No file was generated at the expected path. */
        record FileNotFound(String expectedPath, ImmutableList<String> actualGeneratedPaths) implements Failure {}
        /** The run generated a file no approval was declared for. */
        record UnexpectedFile(String generatedPath) implements Failure {}
    }
}
