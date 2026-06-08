package com.qualityminds.lazyval.testkit.internal.toolchain.kotlin;

import com.google.devtools.ksp.impl.KotlinSymbolProcessing;
import com.google.devtools.ksp.processing.KSPJvmConfig;
import com.google.devtools.ksp.processing.SymbolProcessorProvider;
import com.qualityminds.lazyval.testkit.dependencies.Dependency;
import com.qualityminds.lazyval.testkit.scenarios.Scenario;
import kotlin.KotlinVersion;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * For Kotlin, KSP processing and Kotlin compiler are two separate steps.
 * This toolchains combines both for a complete integration cycle.
 */
public class ToolchainSetup {

    private static final Dependency kotlinStdlib = new Dependency("org.jetbrains.kotlin", "kotlin-stdlib", KotlinVersion.CURRENT.toString());
    private final KotlinSymbolProcessing kspSetup;
    private final KotlinCompilerSetup kotlinSetup;
    private final JavaCompilerSetup javaSetup;
    private final LogCollector logCollector;
    private final ClassLoader symbolProcessorClassloader;
    private final ProjectLayout layout;

    private ToolchainSetup(KotlinSymbolProcessing kspSetup, KotlinCompilerSetup kotlinSetup, JavaCompilerSetup javaSetup, LogCollector logCollector, ClassLoader symbolProcessorClassloader, ProjectLayout layout) {
        this.kspSetup = kspSetup;
        this.kotlinSetup = kotlinSetup;
        this.javaSetup = javaSetup;
        this.logCollector = logCollector;
        this.symbolProcessorClassloader = symbolProcessorClassloader;
        this.layout = layout;
    }

    public ToolchainResult run() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            // Set the context classloader so ServiceLoader inside the symbol processor can find SPI providers
            Thread.currentThread().setContextClassLoader(symbolProcessorClassloader);

            var exitCode = kspSetup.execute();
            var kspSuccess = exitCode == KotlinSymbolProcessing.ExitCode.OK;
            var kotlinSuccess = false;
            var javacSuccess = false;

            if (kspSuccess) {
                kotlinSuccess = kotlinSetup.run();
                if (kotlinSuccess) {
                    javacSuccess = javaSetup.run();
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
                    kspSuccess,
                    kotlinSuccess,
                    javacSuccess,
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
        var layout = new ProjectLayout(projectDir);
        var symbolProcessorClassloader = createSymbolProcessorClassloader(classLoader, scenarioDescriptor);
        var logCollector = new LogCollector();
        var kspSetup = setupKsp2(symbolProcessorClassloader, layout, scenarioDescriptor, logCollector);
        var kotlinSetup = KotlinCompilerSetup.setup(classLoader, layout, scenarioDescriptor, logCollector);
        var javaSetup = JavaCompilerSetup.setup(layout, scenarioDescriptor, logCollector);
        return new ToolchainSetup(kspSetup, kotlinSetup, javaSetup, logCollector, symbolProcessorClassloader, layout);
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
            ProjectLayout layout,
            Scenario.Descriptor scenarioDescriptor,
            LogCollector logCollector) {
        try {
            var additionalClasspath = new ArrayList<>(CoreModuleDependency.RESOLVED_FILE.toSet());
            // Real Kotlin projects always have kotlin-stdlib on KSP's classpath; without it,
            // stdlib types (incl. @kotlin.jvm.Transient) resolve as <error>.
            kotlinStdlib.resolve().forEach(additionalClasspath::add);
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
                    builder.setJdkHome(Path.of(System.getProperty("java.home")).toFile());
                    builder.setProjectBaseDir(layout.projectDir().toFile());
                    builder.setOutputBaseDir(Files.createDirectories(layout.buildDir()).toFile());
                    builder.setClassOutputDir(Files.createDirectories(layout.classes()).toFile());
                    builder.setJavaOutputDir(Files.createDirectories(layout.kspJavaOutput()).toFile());
                    builder.setKotlinOutputDir(Files.createDirectories(layout.kspKotlinOutput()).toFile());
                    builder.setResourceOutputDir(Files.createDirectories(layout.kspResourceOutput()).toFile());
                    builder.setCachesDir(Files.createDirectories(layout.kspCachesDir()).toFile());
                    builder.setSourceRoots(allSources);
                    builder.setProcessorOptions(scenarioDescriptor.options().toMap());
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
