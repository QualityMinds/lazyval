package com.qualityminds.lazyval.testkit.internal.toolchain.kotlin;

import com.qualityminds.lazyval.testkit.scenarios.Scenario;
import org.eclipse.collections.api.list.ImmutableList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Three-step pipeline that mirrors what a real Gradle build does for a Kotlin module with KSP enabled:
 * {@link Step#KSP} → {@link Step#KOTLINC} → {@link Step#JAVAC}. Each step depends on the previous one
 * succeeding; the pipeline short-circuits on the first failure.
 * <p>
 * Holds an open {@link java.net.URLClassLoader} for KSP symbol-processor SPI discovery. Callers must
 * {@link #close()} the toolchain (or use try-with-resources) to release the file handles.
 * <p>
 * The toolchain layout — where KSP writes Java sources, Kotlin sources and resources — is exposed via
 * the static helpers {@link #kspJavaOutputDir}, {@link #kspKotlinOutputDir} and
 * {@link #kspResourceOutputDir} so that consumers (the testkit's collectors and its public path
 * helpers) share one source of truth instead of duplicating string literals.
 */
public class KotlinToolchain implements AutoCloseable {

    /** Root for KSP-emitted Java sources ({@code <projectDir>/build/generated/ksp/java/}). */
    public static Path kspJavaOutputDir(Path projectDir) {
        return new ProjectLayout(projectDir).kspJavaOutput();
    }

    /** Root for KSP-emitted Kotlin sources ({@code <projectDir>/build/generated/ksp/kotlin/}). */
    public static Path kspKotlinOutputDir(Path projectDir) {
        return new ProjectLayout(projectDir).kspKotlinOutput();
    }

    /** Root for KSP-emitted resources ({@code <projectDir>/build/generated/ksp/resources/}). */
    public static Path kspResourceOutputDir(Path projectDir) {
        return new ProjectLayout(projectDir).kspResourceOutput();
    }

    /**
     * Module name handed to both KSP and kotlinc. The two have to agree: Kotlin mangles the JVM name of
     * an {@code internal} member to {@code name$module}, so a KSP step that believes the module is called
     * something else than kotlinc does would report JVM names that never appear in the bytecode. KSP takes
     * it via {@code KSPJvmConfig.setModuleName}, kotlinc via {@code -module-name} — which defaults to
     * {@code main} when left out, silently disagreeing with whatever KSP was told.
     */
    static final String MODULE_NAME = "test";

    private final KspStep kspStep;
    private final KotlinCompileStep kotlinStep;
    private final JavaCompileStep javaStep;
    private final LogCollector logCollector;
    private final ProjectLayout layout;

    private KotlinToolchain(KspStep kspStep, KotlinCompileStep kotlinStep, JavaCompileStep javaStep,
                            LogCollector logCollector, ProjectLayout layout) {
        this.kspStep = kspStep;
        this.kotlinStep = kotlinStep;
        this.javaStep = javaStep;
        this.logCollector = logCollector;
        this.layout = layout;
    }

    public Result run() {
        var outcomes = new EnumMap<Step, StepOutcome>(Step.class);
        try {
            var kspOutcome = kspStep.run();
            outcomes.put(Step.KSP, kspOutcome);
            if (kspOutcome.isSuccessful()) {
                var kotlinOutcome = kotlinStep.run();
                outcomes.put(Step.KOTLINC, kotlinOutcome);
                if (kotlinOutcome.isSuccessful()) {
                    outcomes.put(Step.JAVAC, javaStep.run());
                }
            }

            var generatedJavaSources = walkFiles(layout.kspJavaOutput());
            var generatedKotlinSources = walkFiles(layout.kspKotlinOutput());
            var generatedResources = walkFiles(layout.kspResourceOutput());

            return new Result(
                    outcomes,
                    generatedJavaSources,
                    generatedKotlinSources,
                    generatedResources,
                    logCollector.getErrors(),
                    logCollector.getWarnings()
            );
        } catch (Exception e) {
            throw new RuntimeException("Toolchain execution failed", e);
        }
    }

    private static SortedSet<Path> walkFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return new TreeSet<>();
        }
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(p -> !Files.isDirectory(p))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    @Override
    public void close() throws IOException {
        kspStep.close();
    }

    public static KotlinToolchain create(
            ClassLoader classLoader,
            Path projectDir,
            Scenario.Descriptor scenarioDescriptor) {
        var layout = new ProjectLayout(projectDir);
        var logCollector = new LogCollector();
        var kspStep = KspStep.create(classLoader, layout, scenarioDescriptor, logCollector);
        var kotlinStep = KotlinCompileStep.create(classLoader, layout, scenarioDescriptor, logCollector);
        var javaStep = JavaCompileStep.create(layout, scenarioDescriptor, logCollector);
        return new KotlinToolchain(kspStep, kotlinStep, javaStep, logCollector, layout);
    }

    /**
     * Result of a {@link KotlinToolchain} run.
     *
     * @param stepOutcomes           records the outcome of every step that was started. Steps that never
     *                               started (because an earlier one failed, or when Javac wasn't started
     *                               because no Java files were present) do not appear in the map.
     * @param generatedJavaSources   Java sources emitted by KSP under {@code build/generated/ksp/java/}
     * @param generatedKotlinSources Kotlin sources emitted by KSP under {@code build/generated/ksp/kotlin/}
     * @param generatedResources     non-source artifacts emitted by KSP under
     *                               {@code build/generated/ksp/resources/} (META-INF/services entries,
     *                               properties files, etc.)
     * @param errors                 errors that occurred during compilation
     * @param warnings               warnings that occurred during compilation
     */
    public record Result(Map<Step, StepOutcome> stepOutcomes,
                         SortedSet<Path> generatedJavaSources,
                         SortedSet<Path> generatedKotlinSources,
                         SortedSet<Path> generatedResources,
                         ImmutableList<String> errors,
                         ImmutableList<String> warnings) {

        public boolean isSuccessful() {
            return stepOutcomes.values().stream().allMatch(StepOutcome::isSuccessful);
        }

        /** True when the run produced neither Java/Kotlin sources nor resources. */
        public boolean generatedNoFiles() {
            return generatedJavaSources.isEmpty()
                    && generatedKotlinSources.isEmpty()
                    && generatedResources.isEmpty();
        }
    }
}
