package com.qualityminds.lazyval.testkit;

import com.qualityminds.lazyval.testkit.internal.toolchain.java.JavaToolchain;
import com.qualityminds.lazyval.testkit.internal.toolchain.kotlin.KotlinToolchain;
import com.qualityminds.lazyval.testkit.scenarios.Scenario;
import com.qualityminds.lazyval.testkit.scenarios.ScenarioFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
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
            try (var toolchain = KotlinToolchain.create(
                    Thread.currentThread().getContextClassLoader(),
                    projectDir,
                    scenario.desc())) {
                return convertToolchainResult(toolchain.run());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private Testresult.Kotlin convertToolchainResult(KotlinToolchain.Result toolchainResult){
            if(toolchainResult.isSuccessful()){
                if(toolchainResult.generatedNoFiles()){
                    return new Testresult.Kotlin.NothingGenerated();
                }
                var generatedFileNames = Stream.concat(
                        toolchainResult.generatedJavaFiles().stream(),
                        toolchainResult.generatedKotlinFiles().stream())
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
     * </ul>
     */
    public static final class Java extends Testkit<Scenario.Java, Testresult.Java> {

        private Java(){}

        @Override
        public Testresult.Java run(Path projectDir, Scenario.Java scenario) {
            cleanProjectDir(projectDir);
            var toolchain = JavaToolchain.create(projectDir, scenario.desc());
            var result = toolchain.run();
            return convertToolchainResult(result);
        }

        private Testresult.Java convertToolchainResult(JavaToolchain.Result toolchainResult){
            if(toolchainResult.isSuccessful()){
                if(toolchainResult.generatedNoFiles()){
                    return new Testresult.Java.NothingGenerated();
                }
                var generatedFileNames = toolchainResult.generatedFiles().stream().map(s -> s.getFileName().toString()).collect(toImmutableList());
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
