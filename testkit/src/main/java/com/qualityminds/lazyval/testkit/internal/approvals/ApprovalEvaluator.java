package com.qualityminds.lazyval.testkit.internal.approvals;

import com.qualityminds.lazyval.testkit.Approval;
import com.qualityminds.lazyval.testkit.internal.toolchain.diff.ComparisonResult;
import com.qualityminds.lazyval.testkit.internal.toolchain.diff.Diff;
import com.qualityminds.lazyval.testkit.internal.toolchain.diff.ImportSort;
import com.qualityminds.lazyval.testkit.internal.toolchain.diff.RenderOptions;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Toolchain-agnostic core of the approval-testing flow.
 * <p>
 * Given a snapshot of files the toolchain produced — split by kind into {@link GeneratedFiles} — and a
 * list of {@link Approval approval definitions}, returns an {@link Outcome} describing whether the
 * strict closed-set contract was satisfied: every approval matched the right kind of file and no extra
 * files were generated. The {@code Testkit} translates the toolchain-agnostic outcome into the public
 * {@code Testresult.{Java,Kotlin}.{Approved,ApprovalMismatch}} variant.
 * <p>
 * Per-kind dispatch matters: a {@link Approval.JavaSource} only matches files under the Java-source
 * output root, a {@link Approval.KotlinSource} only matches under the Kotlin-source root, and
 * {@link Approval.Resource}/{@link Approval.ServiceLoader} only match under the resource root. Without
 * this dispatch, a typo (e.g. {@code JavaSource} for a {@code .kt} file under the Kotlin output) would
 * silently pass because both kinds share a relative-path namespace.
 * <p>
 * Pure function over the inputs aside from the file reads of the generated files themselves — which
 * makes this directly testable in the testkit module with files placed in a {@code @TempDir}, without
 * any compiler or annotation processor on the classpath.
 */
public final class ApprovalEvaluator {

    private ApprovalEvaluator() {}

    /**
     * Per-kind snapshot of files the toolchain produced. Each map is keyed by the file's
     * slash-separated path <em>relative to its respective output root</em>; values are absolute paths
     * read on demand for content comparison.
     * <p>
     * For the Java testkit (javac), {@code kotlinSources} is always empty since javac doesn't emit
     * {@code .kt}. For the Kotlin testkit (KSP), all three maps may be populated.
     */
    public record GeneratedFiles(Map<String, Path> javaSources,
                                  Map<String, Path> kotlinSources,
                                  Map<String, Path> resources) {

        public GeneratedFiles {
            Objects.requireNonNull(javaSources, "javaSources must not be null");
            Objects.requireNonNull(kotlinSources, "kotlinSources must not be null");
            Objects.requireNonNull(resources, "resources must not be null");
        }

        /** Empty for tests / edge cases where nothing was generated. */
        public static GeneratedFiles empty() {
            return new GeneratedFiles(Map.of(), Map.of(), Map.of());
        }
    }

    /**
     * Evaluates the given approvals against the per-kind snapshot of generated files.
     *
     * @param produced  the per-kind file snapshot the toolchain produced
     * @param approvals the approval definitions to verify (may be empty, though in that case the
     *                  testkit short-circuits to a different result type)
     * @return {@link Outcome.Approved} if every approval matched and no surplus files were generated,
     *         otherwise {@link Outcome.Mismatch} carrying one {@link Failure} per problem
     */
    public static Outcome evaluate(GeneratedFiles produced, Approval... approvals) {
        Objects.requireNonNull(produced, "produced must not be null");
        Objects.requireNonNull(approvals, "approvals must not be null");

        var failures = Lists.mutable.<Failure>empty();
        var claimedJava = new HashSet<String>();
        var claimedKotlin = new HashSet<String>();
        var claimedResource = new HashSet<String>();

        for (var approval : approvals) {
            Map<String, Path> targetMap;
            Set<String> claimed;
            if (approval instanceof Approval.JavaSource) {
                targetMap = produced.javaSources();
                claimed = claimedJava;
            } else if (approval instanceof Approval.KotlinSource) {
                targetMap = produced.kotlinSources();
                claimed = claimedKotlin;
            } else {
                // Resource and ServiceLoader both live under the resource root.
                targetMap = produced.resources();
                claimed = claimedResource;
            }

            var path = approval.generatedPath();
            var file = targetMap.get(path);
            if (file == null) {
                // FileNotFound lists only the paths under this approval's kind-specific root — that
                // matches the user's mental model when reading the diagnostic.
                failures.add(new Failure.FileNotFound(path, sortedKeys(targetMap)));
                continue;
            }
            claimed.add(path);
            var actual = readFile(file);
            var expected = approval.expectedContent();
            if (isSource(approval)) {
                // Source-only normalization: collapse import reorderings (generator version drift,
                // IDE auto-format on hand-edited fixtures) so they don't surface as diffs.
                actual = ImportSort.sort(actual);
                expected = ImportSort.sort(expected);
            }
            if (Diff.compare(actual, expected) instanceof ComparisonResult.Mismatch mismatch) {
                failures.add(new Failure.ContentDiffers(path, mismatch.render(RenderOptions.plain())));
            }
        }

        // Closed-set check, per kind: any file the run produced that no approval of that kind claimed
        // is a failure. Iterating per-kind catches the wrong-variant case: a Kotlin source unclaimed
        // by a JavaSource approval surfaces as UnexpectedFile here, paired with the JavaSource's
        // FileNotFound emitted above.
        collectUnexpected(produced.javaSources(), claimedJava, failures);
        collectUnexpected(produced.kotlinSources(), claimedKotlin, failures);
        collectUnexpected(produced.resources(), claimedResource, failures);

        if (failures.isEmpty()) {
            return new Outcome.Approved(allGeneratedPaths(produced));
        }
        return new Outcome.Mismatch(failures.toImmutable());
    }

    private static void collectUnexpected(Map<String, Path> map, Set<String> claimed, MutableList<Failure> failures) {
        Lists.immutable.ofAll(map.keySet()).toSortedList().forEach(path -> {
            if (!claimed.contains(path)) {
                failures.add(new Failure.UnexpectedFile(path));
            }
        });
    }

    private static ImmutableList<String> sortedKeys(Map<String, Path> map) {
        return Lists.immutable.ofAll(map.keySet()).toSortedList().toImmutable();
    }

    private static ImmutableList<String> allGeneratedPaths(GeneratedFiles produced) {
        // Deduplicate across kinds: in the (contrived) case where the same relative path appears under
        // two roots, the file is still listed only once in the Approved enumeration. The dispatch makes
        // sure each approval claims the correct underlying file regardless.
        var all = new java.util.TreeSet<String>();
        all.addAll(produced.javaSources().keySet());
        all.addAll(produced.kotlinSources().keySet());
        all.addAll(produced.resources().keySet());
        return Lists.immutable.ofAll(all);
    }

    private static boolean isSource(Approval approval) {
        return approval instanceof Approval.JavaSource
                || approval instanceof Approval.KotlinSource;
    }

    private static String readFile(Path file) {
        try {
            return Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read generated file: " + file, e);
        }
    }

    /** The outcome of an evaluation. */
    public sealed interface Outcome {
        /**
         * Every approval matched its file and no surplus files were generated.
         *
         * @param generatedFiles complete enumeration of files the run produced, sorted and deduplicated
         *                       across kinds
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
        /** No file was generated at the expected path (under the approval's kind-specific root). */
        record FileNotFound(String expectedPath, ImmutableList<String> actualGeneratedPaths) implements Failure {}
        /** The run generated a file no approval was declared for. */
        record UnexpectedFile(String generatedPath) implements Failure {}
    }
}
