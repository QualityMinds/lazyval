package com.qualityminds.lazyval.testkit;

import com.qualityminds.lazyval.testkit.internal.toolchain.java.CompilerResult;
import com.qualityminds.lazyval.testkit.internal.toolchain.java.CompilerSetup;
import com.qualityminds.lazyval.testkit.internal.toolchain.kotlin.ToolchainResult;
import com.qualityminds.lazyval.testkit.internal.toolchain.kotlin.ToolchainSetup;
import com.qualityminds.lazyval.testkit.scenarios.Scenario;
import com.qualityminds.lazyval.testkit.scenarios.ScenarioFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

import static org.eclipse.collections.impl.collector.Collectors2.toImmutableList;

/**
 * The entrypoint to the Testkit. It provides a typed and fluent API to configure a testcase which either runs
 * Lazyvals Java annotation processor or the Kotlin KSP2 processor.
 * <p>
 * The testkit expects a folder and a {@link Scenario}.<br>
 * The folder is used to set up a complete toolchain and is cleaned before execution. In general testframeworks
 * provide a way to inject a temp-directory unique for each test which is what should be used.
 *
 * @param <S> scenario type used by either Java or Kotlin testkit
 * @param <R> result type used by either Java or Kotlin testkit
 */
public sealed abstract class Testkit<S extends Scenario, R extends Testresult> {

    private Testkit(){}

    /**
     * Runs the concrete Scenario on the given project directory.
     * @param projectDir the project directory used to compile the scenario.
     * @param scenario the scenario to run.
     * @return the test result
     */
    public abstract R run(Path projectDir, S scenario);

    /**
     * Builds and runs the given scenario factory on the given project directory.
     * Convenience method, not having to call build() on the scenario factory.
     * @param projectDir the project directory used to compile the scenario.
     * @param scenarioFactory the scenario to run.
     * @return the test result
     */
    public R run(Path projectDir, ScenarioFactory<S> scenarioFactory){
        return run(projectDir, scenarioFactory.build());
    }

    /**
     * Prepares the testkit to be run with the Java annotation processor.
     * @return a Java-testkit instance
     * @throws IllegalStateException if Lazyvals Java annotation processor is not on the classpath.
     */
    public static Testkit.Java java(){
        if(isProcessorMissingOnClasspath("com.qualityminds.lazyval.processor.LazyvalProcessor")){
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
        if(isProcessorMissingOnClasspath("com.qualityminds.lazyval.ksp.LazyvalSymbolProcessor")){
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
     * Kotlin-specific testkit which accepts only {@link Scenario.Kotlin} scenarios and returns {@link Testresult.Kotlin}.
     * <p>
     * Runs KSP2 and the Kotlin compiler with dependencies configured within the scenario, collects compiler output,
     * and transforms the result to one of the following results:
     * <ul>
     *     <li>{@link Testresult.Kotlin.Success}</li>
     *     <li>{@link Testresult.Kotlin.SuccessWithWarnings}</li>
     *     <li>{@link Testresult.Kotlin.NothingGenerated}</li>
     *     <li>{@link Testresult.Kotlin.Failure}</li>
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

    /**
     * Java-specific testkit which accepts only {@link Scenario.Java} scenarios and returns {@link Testresult.Java}.
     * <p>
     * Runs Javac with dependencies configured within the scenario, collects compiler output, and transforms the result
     * to one of the following results:
     * <ul>
     *     <li>{@link Testresult.Java.Success}</li>
     *     <li>{@link Testresult.Java.SuccessWithWarnings}</li>
     *     <li>{@link Testresult.Java.NothingGenerated}</li>
     *     <li>{@link Testresult.Java.Failure}</li>
     * </ul>
     */
    public static final class Java extends Testkit<Scenario.Java, Testresult.Java> {

        private Java(){}

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
