package de.qualityminds.lazyval.testkit.toolchain.kotlin;

import com.google.devtools.ksp.impl.KotlinSymbolProcessing;
import com.google.devtools.ksp.processing.KSPJvmConfig;
import com.google.devtools.ksp.processing.SymbolProcessorProvider;
import de.qualityminds.lazyval.testkit.dependencies.Dependency;
import de.qualityminds.lazyval.testkit.scenarios.Scenario;
import kotlin.KotlinVersion;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * For Kotlin, KSP processing and Kotlin compiler are two separate steps.
 * This toolchains combines both for a complete integration cycle.
 */
public class ToolchainSetup {

    private final KotlinSymbolProcessing kspSetup;
    private final KotlinCompilerSetup kotlinSetup;
    private final LogCollector logCollector;
    private final ClassLoader symbolProcessorClassloader;

    private ToolchainSetup(KotlinSymbolProcessing kspSetup, KotlinCompilerSetup kotlinSetup, LogCollector logCollector, ClassLoader symbolProcessorClassloader) {
        this.kspSetup = kspSetup;
        this.kotlinSetup = kotlinSetup;
        this.logCollector = logCollector;
        this.symbolProcessorClassloader = symbolProcessorClassloader;
    }

    public ToolchainResult run() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            // Set the context classloader so ServiceLoader inside the symbol processor can find SPI providers
            Thread.currentThread().setContextClassLoader(symbolProcessorClassloader);

            var exitCode = kspSetup.execute();
            var kotlinResult = false;

            if (exitCode == KotlinSymbolProcessing.ExitCode.OK) {
                kotlinResult = kotlinSetup.run();
            }

            var kspConfig = (KSPJvmConfig) kspSetup.getKspConfig();
            var generatedJavaFiles = Files.walk(kspConfig.getJavaOutputDir().toPath())
                    .filter(p -> !Files.isDirectory(p))
                    .collect(Collectors.toCollection(TreeSet::new));

            var generatedKotlinFiles = Files.walk(kspConfig.getKotlinOutputDir().toPath())
                    .filter(p -> !Files.isDirectory(p))
                    .collect(Collectors.toCollection(TreeSet::new));

            return new ToolchainResult(
                    exitCode == KotlinSymbolProcessing.ExitCode.OK,
                    kotlinResult,
                    generatedJavaFiles,
                    generatedKotlinFiles,
                    logCollector.getErrors(),
                    logCollector.getWarnings()
            );
        } catch (Exception e) {
            throw new RuntimeException("Toolchain execution failed", e);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    public static ToolchainSetup setupTask(
            ClassLoader classLoader,
            Path projectDir,
            Scenario.Descriptor scenarioDescriptor) {
        var symbolProcessorClassloader = createSymbolProcessorClassloader(classLoader, scenarioDescriptor);
        var logCollector = new LogCollector();
        var kspSetup = setupKsp2(symbolProcessorClassloader, projectDir, scenarioDescriptor, logCollector);
        var kotlinSetup = KotlinCompilerSetup.setup(classLoader, projectDir, scenarioDescriptor, logCollector);
        return new ToolchainSetup(kspSetup, kotlinSetup, logCollector, symbolProcessorClassloader);
    }


    private static URLClassLoader createSymbolProcessorClassloader(
            ClassLoader parentClassLoader,
            Scenario.Descriptor scenarioDescriptor) {
        var additionalClasspath = new ArrayList<>(CoreModuleDependency.RESOLVED_FILE.toSet());
        scenarioDescriptor.dependencies().stream()
                .map(Dependency::resolve)
                .forEach(deps -> additionalClasspath.addAll(deps.toSet()));

        return new URLClassLoader(
                additionalClasspath.stream().map(f -> {
                    try {
                        return f.toURI().toURL();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).toArray(java.net.URL[]::new),
                parentClassLoader
        );
    }

    private static KotlinSymbolProcessing setupKsp2(
            URLClassLoader symbolProcessorClassloader,
            Path projectDir,
            Scenario.Descriptor scenarioDescriptor,
            LogCollector logCollector) {
        try {
            var additionalClasspath = new ArrayList<>(CoreModuleDependency.RESOLVED_FILE.toSet());
            scenarioDescriptor.dependencies().stream().map(Dependency::resolve).forEach(x -> additionalClasspath.addAll(x.toSet()));

            var processorProvidersSearch = ServiceLoader.load(
                    symbolProcessorClassloader.loadClass("com.google.devtools.ksp.processing.SymbolProcessorProvider"),
                    symbolProcessorClassloader
            );

            var compilationUnit = new ArrayList<>(additionalClasspath);

            @SuppressWarnings("unchecked")
            var processorProviders = (List<SymbolProcessorProvider>) processorProvidersSearch.stream()
                    .map(ServiceLoader.Provider::get)
                    .toList();

            Map<String, String> options;
            if (!scenarioDescriptor.disabledGenerators().isEmpty()) {
                options = Map.of("lazyval.disabledGenerators", String.join(",", scenarioDescriptor.disabledGenerators()));
            } else {
                options = Map.of();
            }

            var allSources = Stream.concat(
                    Stream.of(scenarioDescriptor.source()),
                    scenarioDescriptor.additionalSources().stream()
            ).toList();

            var kotlinVersion = KotlinVersion.CURRENT;
            var builder = new KSPJvmConfig.Builder();
                    builder.setJvmTarget("17");
                    builder.setLanguageVersion(kotlinVersion.toString());
                    builder.setApiVersion(kotlinVersion.getMajor() + "." + kotlinVersion.getMinor());
                    builder.setModuleName("test");
                    builder.setProjectBaseDir(projectDir.toFile());
                    builder.setOutputBaseDir(Files.createDirectories(projectDir.resolve("build")).toFile());
                    builder.setClassOutputDir(Files.createDirectories(projectDir.resolve("build/classes")).toFile());
                    builder.setJavaOutputDir(Files.createDirectories(projectDir.resolve("build/generated/ksp/java")).toFile());
                    builder.setKotlinOutputDir(Files.createDirectories(projectDir.resolve("build/generated/ksp/kotlin")).toFile());
                    builder.setResourceOutputDir(Files.createDirectories(projectDir.resolve("build/generated/ksp/kotlin")).toFile());
                    builder.setCachesDir(Files.createDirectories(projectDir.resolve("build/resources")).toFile());
                    builder.setSourceRoots(allSources);
                    builder.setProcessorOptions(options);
                    builder.setLibraries(List.copyOf(compilationUnit));
            var config = builder.build();

            return new KotlinSymbolProcessing(
                    config,
                    processorProviders,
                    new KspSlf4jLogger(logCollector)
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup KSP", e);
        }
    }
}
