package com.qualityminds.lazyval.testkit.internal.toolchain.kotlin;

import com.qualityminds.lazyval.testkit.scenarios.Scenario;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Three-step pipeline that mirrors what a real Gradle build does for a Kotlin module with KSP enabled:
 * {@link Step#KSP} → {@link Step#KOTLINC} → {@link Step#JAVAC}. Each step depends on the previous one
 * succeeding; the pipeline short-circuits on the first failure.
 * <p>
 * Holds an open {@link java.net.URLClassLoader} for KSP symbol-processor SPI discovery. Callers must
 * {@link #close()} the toolchain (or use try-with-resources) to release the file handles.
 */
public class KotlinToolchain implements AutoCloseable {

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

    public ToolchainResult run() {
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

            TreeSet<Path> generatedJavaFiles;
            try (var stream = Files.walk(layout.kspJavaOutput())) {
                generatedJavaFiles = stream
                        .filter(p -> !Files.isDirectory(p))
                        .collect(Collectors.toCollection(TreeSet::new));
            }

            TreeSet<Path> generatedKotlinFiles;
            try (var stream = Files.walk(layout.kspKotlinOutput())) {
                generatedKotlinFiles = stream
                        .filter(p -> !Files.isDirectory(p))
                        .collect(Collectors.toCollection(TreeSet::new));
            }

            return new ToolchainResult(
                    outcomes,
                    generatedJavaFiles,
                    generatedKotlinFiles,
                    logCollector.getErrors(),
                    logCollector.getWarnings()
            );
        } catch (Exception e) {
            throw new RuntimeException("Toolchain execution failed", e);
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
}
