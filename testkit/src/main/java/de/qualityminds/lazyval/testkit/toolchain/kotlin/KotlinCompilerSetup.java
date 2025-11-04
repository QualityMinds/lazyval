package de.qualityminds.lazyval.testkit.toolchain.kotlin;

import de.qualityminds.lazyval.collections.NonEmptySet;
import de.qualityminds.lazyval.testkit.dependencies.Dependency;
import de.qualityminds.lazyval.testkit.scenarios.Scenario;
import kotlin.KotlinVersion;
import ksp.com.google.common.collect.ImmutableList;
import org.jetbrains.kotlin.buildtools.api.CompilationService;
import org.jetbrains.kotlin.buildtools.api.CompilerExecutionStrategyConfiguration;
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi;
import org.jetbrains.kotlin.buildtools.api.ProjectId;
import org.jetbrains.kotlin.buildtools.api.jvm.JvmCompilationConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@ExperimentalBuildToolsApi
public record KotlinCompilerSetup(Path projectDir, CompilationService service,
                                  CompilerExecutionStrategyConfiguration strategyConfig,
                                  JvmCompilationConfiguration compilationConfig, List<File> sources,
                                  List<File> compilerClasspath) {



    private List<File> findAllSourceFiles(File file) {
        if (file.isDirectory()) {
            var files = file.listFiles();
            if (files == null) {
                return List.of();
            }
            return Stream.of(files)
                    .flatMap(f -> findAllSourceFiles(f).stream())
                    .toList();
        } else {
            return List.of(file);
        }
    }

    public boolean run() {
        try {
            // make sure to also compile the generated sources from KSP
            // because the sources are not available during setup, we have to defer this to run()
            var kspJavaSources = findAllSourceFiles(projectDir.resolve("build/generated/ksp/java").toFile());
            var kspKotlinSources = findAllSourceFiles(projectDir.resolve("build/generated/ksp/kotlin").toFile());

            var allSources = new ArrayList<>(sources);
            allSources.addAll(kspJavaSources);
            allSources.addAll(kspKotlinSources);

            var classpathString = compilerClasspath.stream()
                    .map(File::getAbsolutePath)
                    .reduce((a, b) -> a + File.pathSeparator + b)
                    .orElse("");

            var arguments = List.of(
                    // destination directory
                    "-d", projectDir.resolve("build/classes").toAbsolutePath().toString(),
                    // classpath
                    "-classpath", classpathString,
                    // std-lib and kotlin-reflect will be added to classpath manually
                    // because the kotlin compiler otherwise expects a kotlin-stdlib on the filesystem
                    "-no-stdlib", "-no-reflect"
            );

            var result = service.compileJvm(
                    ProjectId.ProjectUUID.Companion.RandomProjectUUID(),
                    strategyConfig,
                    compilationConfig,
                    allSources,
                    arguments
            );

            return switch (result) {
                case COMPILATION_SUCCESS -> true;
                case COMPILATION_ERROR, COMPILATION_OOM_ERROR, COMPILER_INTERNAL_ERROR -> false;
            };
        } catch (Exception e) {
            throw new RuntimeException("Kotlin compilation failed", e);
        }
    }

    public static KotlinCompilerSetup setup(
            ClassLoader classLoader,
            Path projectDir,
            Scenario.Descriptor scenarioDescriptor, LogCollector logCollector) {
        try {
            ImmutableList.Builder<Dependency> builder = ImmutableList.builder();
            var extended = builder
                    // additional dependencies are needed for the Kotlin compiler
                    .add(new Dependency("org.jetbrains.kotlin", "kotlin-stdlib", KotlinVersion.CURRENT.toString()))
                    .add(new Dependency("org.jetbrains.kotlin", "kotlin-reflect", KotlinVersion.CURRENT.toString()))
                    .addAll(scenarioDescriptor.dependencies()).build();


            var compilerClasspath = Stream.concat(extended.stream().map(Dependency::resolve).flatMap(NonEmptySet::stream), CoreModuleDependency.RESOLVED_FILE.stream()).toList();

            var service = CompilationService.loadImplementation(classLoader);
            var strategyConfig = service.makeCompilerExecutionStrategyConfiguration();
            var compilationConfig = service.makeJvmCompilationConfiguration();

            var incrementalConfig = compilationConfig.makeClasspathSnapshotBasedIncrementalCompilationConfiguration();
            incrementalConfig.setRootProjectDir(projectDir.toFile());
            incrementalConfig.setBuildDir(Files.createDirectories(projectDir.resolve("build")).toFile());
            incrementalConfig.usePreciseJavaTracking(true);
            compilationConfig.useLogger(new KotlinCompilerSlf4jLogger(logCollector));

            var allSources = Stream.concat(
                    Stream.of(scenarioDescriptor.source()),
                    scenarioDescriptor.additionalSources().stream()
            ).toList();

            return new KotlinCompilerSetup(
                    projectDir,
                    service,
                    strategyConfig,
                    compilationConfig,
                    allSources,
                    List.copyOf(compilerClasspath)
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup Kotlin compiler", e);
        }
    }
}
