package de.qualityminds.lazyval.testkit;

import de.qualityminds.lazyval.testkit.scenarios.Scenario;
import de.qualityminds.lazyval.testkit.scenarios.ScenarioFactory;
import de.qualityminds.lazyval.testkit.toolchain.java.CompilerResult;
import de.qualityminds.lazyval.testkit.toolchain.java.CompilerSetup;
import de.qualityminds.lazyval.testkit.toolchain.kotlin.ToolchainResult;
import de.qualityminds.lazyval.testkit.toolchain.kotlin.ToolchainSetup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.eclipse.collections.impl.collector.Collectors2.toImmutableList;

public sealed abstract class Testkit<S extends Scenario, R extends Testresult> {

    /**
     * Runs the concrete Scenario on the given project directory.
     */
    public abstract R run(Path projectDir, S scenario);

    /**
     * Convenience method, not having to call build() on the scenario factory.
     */
    public R run(Path projectDir, ScenarioFactory<S> scenarioFactory){
        return run(projectDir, scenarioFactory.build());
    }

    /**
     * Prepares the testkit to be run with the Java annotation processor.
     */
    public static Testkit.Java java(){
        if(isProcessorMissingOnClasspath("de.qualityminds.lazyval.processor.LazyvalProcessor")){
            throw new IllegalStateException("Lazyval Java processor is not on the classpath. Cannot start Java testkit.");
        }
        return new Testkit.Java();
    }

    /**
     * Prepares the testkit to be run with the KSP2 processor.
     */
    public static Testkit.Kotlin kotlin(){
        if(isProcessorMissingOnClasspath("de.qualityminds.lazyval.ksp.LazyvalSymbolProcessor")){
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

    public static void cleanProjectDir(Path directory) {
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

    public static final class Kotlin extends Testkit<Scenario.Kotlin, Testresult.Kotlin> {

        @Override
        public Testresult.Kotlin run(Path projectDir, Scenario.Kotlin scenario) {
            cleanProjectDir(projectDir);
            var toolchainSetup = setupScenarioToolchain(projectDir, scenario);
            var result = toolchainSetup.run();
            return convertToolchainResult(result);
        }

        private ToolchainSetup setupScenarioToolchain(Path projectDir, Scenario.Kotlin scenario) {
            return ToolchainSetup.setupTask(
                    Thread.currentThread().getContextClassLoader(),
                    projectDir,
                    scenario.desc());
        }

        private Testresult.Kotlin convertToolchainResult(ToolchainResult toolchainResult){
            if(toolchainResult.isSuccessful()){
                if(toolchainResult.generatedNoFiles()){
                    return new Testresult.Kotlin.NothingGenerated();
                }
                if(!toolchainResult.warnings().isEmpty()){
                    return new Testresult.Kotlin.SuccessWithWarnings(
                            Stream.concat(
                                    toolchainResult.generatedJavaFiles().stream(),
                                    toolchainResult.generatedKotlinFiles().stream()
                            ).map(s -> s.getFileName().toString()).collect(toImmutableList()),
                            toolchainResult.warnings());
                }else {
                    return new Testresult.Kotlin.Success(
                            Stream.concat(
                                    toolchainResult.generatedJavaFiles().stream(),
                                    toolchainResult.generatedKotlinFiles().stream()
                            ).map(s -> s.getFileName().toString()).collect(toImmutableList())
                    );
                }
            }else {
                return new Testresult.Kotlin.Failure(toolchainResult.errors());
            }
        }

    }

    public static final class Java extends Testkit<Scenario.Java, Testresult.Java> {

        @Override
        public Testresult.Java run(Path projectDir, Scenario.Java scenario) {
            cleanProjectDir(projectDir);
            var task = CompilerSetup.setupTask(projectDir, scenario.desc());
            var result = task.run();
            return convertToolchainResult(result);
        }

        private Testresult.Java convertToolchainResult(CompilerResult toolchainResult){
            if(toolchainResult.taskResult()){
                var generatedFileName = toolchainResult.generatedFiles().stream().map(s -> s.getFileName().toString()).collect(toImmutableList());
                if(generatedFileName.isEmpty()){
                    return new Testresult.Java.NothingGenerated();
                }
                if(!toolchainResult.getWarnings().isEmpty()){
                    return new Testresult.Java.SuccessWithWarnings(
                            generatedFileName,
                            toolchainResult.getWarnings().stream().map(s -> s.getMessage(Locale.ENGLISH)).collect(toImmutableList()));
                }else {
                    return new Testresult.Java.Success(generatedFileName);
                }
            }else {
                return new Testresult.Java.Failure(toolchainResult.getErrors().stream().map(s -> s.getMessage(Locale.ENGLISH)).collect(toImmutableList()));
            }
        }
    }
}
