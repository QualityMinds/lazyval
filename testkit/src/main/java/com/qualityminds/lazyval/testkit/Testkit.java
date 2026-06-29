package com.qualityminds.lazyval.testkit;

import com.qualityminds.lazyval.testkit.internal.approvals.ApprovalEvaluator;
import com.qualityminds.lazyval.testkit.internal.toolchain.java.JavaToolchain;
import com.qualityminds.lazyval.testkit.internal.toolchain.kotlin.KotlinToolchain;
import com.qualityminds.lazyval.testkit.scenarios.Scenario;
import com.qualityminds.lazyval.testkit.scenarios.ScenarioFactory;
import org.eclipse.collections.api.list.ImmutableList;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.eclipse.collections.impl.collector.Collectors2.toImmutableList;

/**
 * Entry point to the Testkit.
 *
 * <p>The Testkit provides a typed, fluent API for configuring and running a test case
 * that executes either the Lazyvals Java annotation processor or the Kotlin KSP2
 * processor.</p>
 *
 * <p>Each test run requires a project directory and a {@link Scenario}. The project
 * directory is used to create a complete, temporary compiler project for the selected
 * toolchain. It is cleaned before execution and may be modified freely during the
 * test run.</p>
 *
 * <p>In typical usage, the project directory should be provided by the surrounding
 * test framework as a temporary directory that is unique to the current test.</p>
 *
 * <h2>File system requirements</h2>
 *
 * <p>The supplied project directory must support conversion to {@link java.io.File}
 * via {@link Path#toFile()}. In-memory or virtual file systems such as Jimfs or
 * MemoryFileSystem are therefore not supported. This limitation comes from the
 * underlying compiler toolchain, including {@code javac}, {@code kotlinc}, and KSP2,
 * which use {@code java.io.File} and standard file-system access internally.</p>
 *
 * @param <S> scenario type used by either the Java or Kotlin Testkit
 * @param <R> result type used by either the Java or Kotlin Testkit
 */
public sealed abstract class Testkit<S extends Scenario, R extends Testresult> {

    private Testkit(){}

    /**
     * Runs the given scenario in the supplied project directory.
     *
     * @param projectDir the project directory used to compile the scenario; must satisfy
     *                   the file system requirements described in {@link Testkit}
     * @param scenario the scenario to run
     * @return the test result
     */
    public abstract R run(Path projectDir, S scenario);

    /**
     * Runs the given scenario and verifies each {@link Approval} against the files the
     * scenario produced. Returns the corresponding {@code Approved} variant if every approval passed,
     * the {@code ApprovalMismatch} variant if any approval failed, or {@code Failure} if compilation
     * itself failed.
     *
     * @param projectDir          the project directory used to compile the scenario
     * @param scenario            the scenario to run
     * @param approvals one or more approvals to verify after the run
     * @return the test result
     */
    public abstract R run(Path projectDir, S scenario, Approval... approvals);

    /**
     * Builds the given scenario factory and runs the resulting scenario in the supplied
     * project directory.
     *
     * <p>This is a convenience method for callers that do not need to call
     * {@code build()} explicitly.</p>
     *
     * @param projectDir the project directory used to compile the scenario; must satisfy
     *                   the file system requirements described in {@link Testkit}
     * @param scenarioFactory the scenario factory to build and run
     * @return the test result
     */
    public R run(Path projectDir, ScenarioFactory<S> scenarioFactory){
        return run(projectDir, scenarioFactory.build());
    }

    /**
     * Builds the given scenario factory and runs the resulting scenario with approval verification.
     * Symmetric with {@link #run(Path, Scenario, Approval...)} but accepts a factory so
     * callers can chain configuration without an explicit {@code build()} step.
     *
     * @param projectDir          the project directory used to compile the scenario
     * @param scenarioFactory     the scenario factory to build and run
     * @param approvals one or more approvals to verify after the run
     * @return the test result
     */
    public R run(Path projectDir, ScenarioFactory<S> scenarioFactory, Approval... approvals){
        return run(projectDir, scenarioFactory.build(), approvals);
    }

    /**
     * Collection-accepting overload of {@link #run(Path, ScenarioFactory, Approval...)} for Groovy/Kotlin
     * call sites that build their approvals as a {@code List}.
     *
     * @param projectDir the project directory used to compile the scenario
     * @param scenarioFactory the scenario factory to build and run
     * @param approvals approvals to verify after the run
     * @return the test result
     */
    public R run(Path projectDir, ScenarioFactory<S> scenarioFactory, Collection<Approval> approvals){
        return run(projectDir, scenarioFactory.build(), approvals.toArray(Approval[]::new));
    }

    /**
     * Prepares the testkit to be run with the Java annotation processor.
     * @return a Java-testkit instance
     * @throws IllegalStateException if Lazyvals Java annotation processor is not on the classpath.
     */
    public static Testkit.Java java(){
        if(isProcessorMissingOnClasspath("com.qualityminds.lazyval.processor.internal.LazyvalProcessor")){
            throw new IllegalStateException("Lazyval Java processor is not on the classpath. Cannot start Java testkit.");
        }
        return new Testkit.Java();
    }

    /**
     * Prepares the testkit to be run with the KSP2 processor.
     * @return a Kotlin-testkit instance
     * @throws IllegalStateException if Lazyvals KSP2 processor is not on the classpath.
     */
    public static Testkit.Kotlin kotlin(){
        if(isProcessorMissingOnClasspath("com.qualityminds.lazyval.ksp.internal.LazyvalSymbolProcessor")){
            throw new IllegalStateException("Lazyval Kotlin processor is not on the classpath. Cannot start Kotlin testkit.");
        }
        return new Testkit.Kotlin();
    }

    private static boolean isProcessorMissingOnClasspath(String processorClassName) {
        try {
            Class.forName(processorClassName);
            return false;
        } catch (ClassNotFoundException e) {
            return true;
        }
    }

    private static void cleanProjectDir(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }

        try (var stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder())  // Delete files before directories
                    .filter(path -> !path.equals(directory))  // Keep the root directory
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Builds a relative-path → absolute-path map for every file in {@code files} that sits under
     * {@code root}. Files outside the root (or root not existing) yield an empty map. Paths are
     * normalized to use forward slashes so they match the {@link Approval#generatedPath()} convention
     * regardless of platform.
     */
    private static Map<String, Path> collectGeneratedFiles(Path root, SortedSet<Path> files) {
        if (!Files.isDirectory(root)) {
            return Map.of();
        }
        var map = new LinkedHashMap<String, Path>();
        for (Path file : files) {
            if (!file.startsWith(root)) {
                continue;
            }
            var relative = root.relativize(file).toString().replace(java.io.File.separatorChar, '/');
            map.put(relative, file);
        }
        return map;
    }

    /**
     * Kotlin-specific testkit which accepts only {@link Scenario.Kotlin} scenarios and returns {@link Testresult.Kotlin}.
     * <p>
     * Runs KSP2 and the Kotlin compiler with dependencies configured within the scenario, collects compiler output,
     * and transforms the result to one of the following results:
     * <ul>
     *     <li>{@link Testresult.Kotlin.Success}</li>
     *     <li>{@link Testresult.Kotlin.SuccessWithWarnings}</li>
     *     <li>{@link Testresult.Kotlin.NothingGenerated}</li>
     *     <li>{@link Testresult.Kotlin.Failure}</li>
     *     <li>{@link Testresult.Kotlin.Approved} (when approvals are passed and all match)</li>
     *     <li>{@link Testresult.Kotlin.ApprovalMismatch} (when approvals are passed and at least one fails)</li>
     * </ul>
     * <p>
     * In contrast to Javac, which includes the annotation processing, KSP2 is not a Kotlin compiler plugin and runs
     * in a separate process before the actual compilation.
     * Hence, the testkit also runs the compiler to check if the generated code is correct.
     */
    public static final class Kotlin extends Testkit<Scenario.Kotlin, Testresult.Kotlin> {

        private Kotlin(){}

        @Override
        public Testresult.Kotlin run(Path projectDir, Scenario.Kotlin scenario) {
            cleanProjectDir(projectDir);
            try (var toolchain = KotlinToolchain.create(
                    Thread.currentThread().getContextClassLoader(),
                    projectDir,
                    scenario.desc())) {
                return convertToolchainResult(toolchain.run());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Testresult.Kotlin run(Path projectDir, Scenario.Kotlin scenario, Approval... approvals) {
            //noinspection ConstantValue
            if (approvals == null || approvals.length == 0) {
                return run(projectDir, scenario);
            }
            // Kotlin testkit accepts all ApprovalDefinition variants — no pre-flight rejection.
            cleanProjectDir(projectDir);
            try (var toolchain = KotlinToolchain.create(
                    Thread.currentThread().getContextClassLoader(),
                    projectDir,
                    scenario.desc())) {
                var toolchainResult = toolchain.run();
                if (!toolchainResult.isSuccessful()) {
                    return new Testresult.Kotlin.Failure(toolchainResult.errors());
                }
                var outcome = ApprovalEvaluator.evaluate(
                        collectKotlinGeneratedFiles(projectDir, toolchainResult),
                        approvals);
                return toKotlinTestresult(outcome, toolchainResult.warnings());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static Testresult.Kotlin toKotlinTestresult(ApprovalEvaluator.Outcome outcome,
                                                            ImmutableList<String> warnings) {
            if (outcome instanceof ApprovalEvaluator.Outcome.Approved approved) {
                return new Testresult.Kotlin.Approved(approved.generatedFiles(), warnings);
            }
            var mismatch = (ApprovalEvaluator.Outcome.Mismatch) outcome;
            var mapped = mismatch.failures().collect(Kotlin::mapFailure);
            return new Testresult.Kotlin.ApprovalMismatch(mapped);
        }

        private static Testresult.Kotlin.ApprovalMismatch.Failure mapFailure(ApprovalEvaluator.Failure failure) {
            if (failure instanceof ApprovalEvaluator.Failure.ContentDiffers cd) {
                return new Testresult.Kotlin.ApprovalMismatch.Failure.ContentDiffers(cd.generatedPath(), cd.renderedDiff());
            }
            if (failure instanceof ApprovalEvaluator.Failure.FileNotFound fnf) {
                return new Testresult.Kotlin.ApprovalMismatch.Failure.FileNotFound(fnf.expectedPath(), fnf.actualGeneratedPaths());
            }
            if (failure instanceof ApprovalEvaluator.Failure.UnexpectedFile uf) {
                return new Testresult.Kotlin.ApprovalMismatch.Failure.UnexpectedFile(uf.generatedPath());
            }
            throw new IllegalStateException("Unknown failure type: " + failure);
        }

        /**
         * Resolves the absolute filesystem path of a generated Java source under
         * {@code build/generated/ksp/java/}. Hides the KSP output-layout from test code.
         *
         * @param projectDir the project directory used in the run
         * @param relativePath path under the KSP Java output root (slash-separated, package directories included)
         * @return absolute path to the generated Java source
         */
        public Path generatedJavaSourcePath(Path projectDir, String relativePath) {
            return KotlinToolchain.kspJavaOutputDir(projectDir).resolve(relativePath);
        }

        /**
         * Resolves the absolute filesystem path of a generated Kotlin source under
         * {@code build/generated/ksp/kotlin/}. Hides the KSP output-layout from test code.
         *
         * @param projectDir the project directory used in the run
         * @param relativePath path under the KSP Kotlin output root (slash-separated, package directories included)
         * @return absolute path to the generated Kotlin source
         */
        public Path generatedKotlinSourcePath(Path projectDir, String relativePath) {
            return KotlinToolchain.kspKotlinOutputDir(projectDir).resolve(relativePath);
        }

        /**
         * Resolves the absolute filesystem path of a KSP-generated resource under
         * {@code build/generated/ksp/resources/}.
         *
         * @param projectDir the project directory used in the run
         * @param relativePath path under the KSP resource output root (e.g. {@code "META-INF/services/<fqn>"})
         * @return absolute path to the generated resource
         */
        public Path generatedResourcePath(Path projectDir, String relativePath) {
            return KotlinToolchain.kspResourceOutputDir(projectDir).resolve(relativePath);
        }

        private static ApprovalEvaluator.GeneratedFiles collectKotlinGeneratedFiles(Path projectDir, KotlinToolchain.Result result) {
            // Per-kind maps so the evaluator can dispatch on Approval variant. Without this split, a
            // mistyped variant (e.g. JavaSource for a .kt file under ksp/kotlin/) would resolve via
            // path-string equality and silently pass.
            return new ApprovalEvaluator.GeneratedFiles(
                    collectGeneratedFiles(KotlinToolchain.kspJavaOutputDir(projectDir), result.generatedJavaSources()),
                    collectGeneratedFiles(KotlinToolchain.kspKotlinOutputDir(projectDir), result.generatedKotlinSources()),
                    collectGeneratedFiles(KotlinToolchain.kspResourceOutputDir(projectDir), result.generatedResources()));
        }

        private Testresult.Kotlin convertToolchainResult(KotlinToolchain.Result toolchainResult){
            if(toolchainResult.isSuccessful()){
                if(toolchainResult.generatedNoFiles()){
                    return new Testresult.Kotlin.NothingGenerated();
                }
                var generatedFileNames = Stream.concat(
                        toolchainResult.generatedJavaSources().stream(),
                        toolchainResult.generatedKotlinSources().stream())
                        .map(s -> s.getFileName().toString()).collect(toImmutableList());
                if(!toolchainResult.warnings().isEmpty()){
                    return new Testresult.Kotlin.SuccessWithWarnings(
                            generatedFileNames,
                            toolchainResult.warnings());
                }else {
                    return new Testresult.Kotlin.Success(generatedFileNames);
                }
            }else {
                return new Testresult.Kotlin.Failure(toolchainResult.errors());
            }
        }
    }

    /**
     * Java-specific testkit that accepts only {@link Scenario.Java} scenarios and returns {@link Testresult.Java}.
     * <p>
     * Runs Javac with dependencies configured within the scenario, collects compiler output, and transforms the result
     * to one of the following results:
     * <ul>
     *     <li>{@link Testresult.Java.Success}</li>
     *     <li>{@link Testresult.Java.SuccessWithWarnings}</li>
     *     <li>{@link Testresult.Java.NothingGenerated}</li>
     *     <li>{@link Testresult.Java.Failure}</li>
     *     <li>{@link Testresult.Java.Approved} (when approvals are passed and all match)</li>
     *     <li>{@link Testresult.Java.ApprovalMismatch} (when approvals are passed and at least one fails)</li>
     * </ul>
     */
    public static final class Java extends Testkit<Scenario.Java, Testresult.Java> {

        private Java(){}

        @Override
        public Testresult.Java run(Path projectDir, Scenario.Java scenario) {
            cleanProjectDir(projectDir);
            try (var toolchain = JavaToolchain.create(projectDir, scenario.desc())) {
                return convertToolchainResult(toolchain.run());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Testresult.Java run(Path projectDir, Scenario.Java scenario, Approval... approvals) {
            //noinspection ConstantValue
            if (approvals == null || approvals.length == 0) {
                return run(projectDir, scenario);
            }
            rejectKotlinSourceApprovals(approvals);
            cleanProjectDir(projectDir);
            try (var toolchain = JavaToolchain.create(projectDir, scenario.desc())) {
                var toolchainResult = toolchain.run();
                if (!toolchainResult.isSuccessful()) {
                    return new Testresult.Java.Failure(toolchainResult.getErrors());
                }
                var outcome = ApprovalEvaluator.evaluate(
                        collectJavaGeneratedFiles(projectDir, toolchainResult),
                        approvals);
                return toJavaTestresult(outcome, toolchainResult.getWarnings());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /**
         * The Java testkit runs javac only — KSP isn't on the pipeline, so {@code .kt} files are
         * never produced. Fail loudly at the start of the run rather than producing a misleading
         * {@code FileNotFound} for every {@link Approval.KotlinSource}.
         */
        private static void rejectKotlinSourceApprovals(Approval[] approvals) {
            for (var approval : approvals) {
                if (approval instanceof Approval.KotlinSource ks) {
                    throw new IllegalArgumentException(
                            "KotlinSource approvals are only valid for Testkit.kotlin(); "
                                    + "use Testkit.kotlin() to verify '" + ks.generatedPath() + "'");
                }
            }
        }

        private static Testresult.Java toJavaTestresult(ApprovalEvaluator.Outcome outcome,
                                                        ImmutableList<String> warnings) {
            if (outcome instanceof ApprovalEvaluator.Outcome.Approved approved) {
                return new Testresult.Java.Approved(approved.generatedFiles(), warnings);
            }
            var mismatch = (ApprovalEvaluator.Outcome.Mismatch) outcome;
            var mapped = mismatch.failures().collect(Java::mapFailure);
            return new Testresult.Java.ApprovalMismatch(mapped);
        }

        private static Testresult.Java.ApprovalMismatch.Failure mapFailure(ApprovalEvaluator.Failure failure) {
            if (failure instanceof ApprovalEvaluator.Failure.ContentDiffers cd) {
                return new Testresult.Java.ApprovalMismatch.Failure.ContentDiffers(cd.generatedPath(), cd.renderedDiff());
            }
            if (failure instanceof ApprovalEvaluator.Failure.FileNotFound fnf) {
                return new Testresult.Java.ApprovalMismatch.Failure.FileNotFound(fnf.expectedPath(), fnf.actualGeneratedPaths());
            }
            if (failure instanceof ApprovalEvaluator.Failure.UnexpectedFile uf) {
                return new Testresult.Java.ApprovalMismatch.Failure.UnexpectedFile(uf.generatedPath());
            }
            throw new IllegalStateException("Unknown failure type: " + failure);
        }

        /**
         * Resolves the absolute filesystem path of a generated Java source under
         * {@code build/generated/}. Hides the javac output-layout from test code.
         * <p>
         * Example: {@code testkit.generatedSourcePath(projectDir, "test/custom/X.java")}
         * → {@code <projectDir>/build/generated/test/custom/X.java}.
         *
         * @param projectDir the project directory used in the run
         * @param relativePath path under the javac source-output root (slash-separated, package directories included)
         * @return absolute path to the generated source
         */
        public Path generatedSourcePath(Path projectDir, String relativePath) {
            return JavaToolchain.sourceOutputDir(projectDir).resolve(relativePath);
        }

        /**
         * Resolves the absolute filesystem path of a generated resource under
         * {@code build/classes/} (e.g. {@code META-INF/services/...} entries).
         *
         * @param projectDir the project directory used in the run
         * @param relativePath path under the javac class-output root (e.g. {@code "META-INF/services/<fqn>"})
         * @return absolute path to the generated resource
         */
        public Path generatedResourcePath(Path projectDir, String relativePath) {
            return JavaToolchain.classOutputDir(projectDir).resolve(relativePath);
        }

        private static ApprovalEvaluator.GeneratedFiles collectJavaGeneratedFiles(Path projectDir, JavaToolchain.Result result) {
            // Per-kind split so the evaluator can dispatch on Approval variant. javac doesn't emit
            // Kotlin sources, so the kotlinSources map is always empty for the Java testkit.
            return new ApprovalEvaluator.GeneratedFiles(
                    collectGeneratedFiles(JavaToolchain.sourceOutputDir(projectDir), result.generatedSources()),
                    Map.of(),
                    collectGeneratedFiles(JavaToolchain.classOutputDir(projectDir), result.generatedResources()));
        }

        private Testresult.Java convertToolchainResult(JavaToolchain.Result toolchainResult){
            if(toolchainResult.isSuccessful()){
                if(toolchainResult.generatedNoFiles()){
                    return new Testresult.Java.NothingGenerated();
                }
                var generatedFileNames = toolchainResult.generatedSources().stream().map(s -> s.getFileName().toString()).collect(toImmutableList());
                if(!toolchainResult.getWarnings().isEmpty()){
                    return new Testresult.Java.SuccessWithWarnings(
                            generatedFileNames,
                            toolchainResult.getWarnings());
                }else {
                    return new Testresult.Java.Success(generatedFileNames);
                }
            }else {
                return new Testresult.Java.Failure(toolchainResult.getErrors());
            }
        }
    }
}
