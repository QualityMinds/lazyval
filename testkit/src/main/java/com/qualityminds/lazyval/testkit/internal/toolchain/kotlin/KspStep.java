package com.qualityminds.lazyval.testkit.internal.toolchain.kotlin;

import com.google.devtools.ksp.impl.KotlinSymbolProcessing;
import com.google.devtools.ksp.processing.KSPJvmConfig;
import com.google.devtools.ksp.processing.SymbolProcessorProvider;
import com.qualityminds.lazyval.testkit.dependencies.Dependency;
import com.qualityminds.lazyval.testkit.scenarios.Scenario;
import kotlin.KotlinVersion;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * First step in the Kotlin toolchain: runs KSP2 against the scenario sources.
 * <p>
 * Owns the {@link URLClassLoader} used to load {@code SymbolProcessorProvider} SPI implementations and
 * swaps it onto the current thread for the duration of {@link #run()}. The swap is tightly scoped because
 * KSP relies on {@code ServiceLoader} which reads the context classloader; later steps (kotlinc, javac)
 * must not see it.
 * <p>
 * Closing the step releases the classloader's open file handles. Required for clean test repetition.
 */
class KspStep implements AutoCloseable {

    private final KotlinSymbolProcessing ksp;
    private final URLClassLoader processorClassloader;

    private KspStep(KotlinSymbolProcessing ksp, URLClassLoader processorClassloader) {
        this.ksp = ksp;
        this.processorClassloader = processorClassloader;
    }

    StepOutcome run() {
        var original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(processorClassloader);
            var exitCode = ksp.execute();
            return switch (exitCode) {
                case OK -> StepOutcome.SUCCESS;
                case PROCESSING_ERROR -> StepOutcome.COMPILE_ERROR;
            };
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    URLClassLoader processorClassloader() {
        return processorClassloader;
    }

    @Override
    public void close() throws IOException {
        processorClassloader.close();
    }

    static KspStep create(
            ClassLoader parentClassLoader,
            ProjectLayout layout,
            Scenario.Descriptor scenarioDescriptor,
            LogCollector logCollector) {
        var processorClassloader = createProcessorClassloader(parentClassLoader, scenarioDescriptor);
        var ksp = configureKsp(processorClassloader, layout, scenarioDescriptor, logCollector);
        return new KspStep(ksp, processorClassloader);
    }

    private static URLClassLoader createProcessorClassloader(
            ClassLoader parentClassLoader,
            Scenario.Descriptor scenarioDescriptor) {
        var classpath = new ArrayList<>(CoreModuleDependency.RESOLVED_FILE.toSet());
        scenarioDescriptor.dependencies().stream()
                .map(Dependency::resolve)
                .forEach(deps -> classpath.addAll(deps.toSet()));

        return new URLClassLoader(
                classpath.stream().map(f -> {
                    try {
                        return f.toURI().toURL();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).toArray(java.net.URL[]::new),
                parentClassLoader
        );
    }

    private static KotlinSymbolProcessing configureKsp(
            URLClassLoader processorClassloader,
            ProjectLayout layout,
            Scenario.Descriptor scenarioDescriptor,
            LogCollector logCollector) {
        try {
            var libraries = new ArrayList<>(CoreModuleDependency.RESOLVED_FILE.toSet());
            // Real Kotlin projects always have kotlin-stdlib on KSP's classpath; without it,
            // stdlib types (incl. @kotlin.jvm.Transient) resolve as <error>.
            KotlinToolchainDependencies.KOTLIN_STDLIB.resolve().forEach(libraries::add);
            scenarioDescriptor.dependencies().stream().map(Dependency::resolve).forEach(x -> libraries.addAll(x.toSet()));

            var processorProvidersSearch = ServiceLoader.load(
                    processorClassloader.loadClass("com.google.devtools.ksp.processing.SymbolProcessorProvider"),
                    processorClassloader
            );

            @SuppressWarnings("unchecked")
            var processorProviders = (List<SymbolProcessorProvider>) processorProvidersSearch.stream()
                    .map(ServiceLoader.Provider::get)
                    .toList();


            var kotlinVersion = KotlinVersion.CURRENT;
            var builder = new KSPJvmConfig.Builder();
            builder.setJvmTarget("17");
            builder.setLanguageVersion(kotlinVersion.toString());
            builder.setApiVersion(kotlinVersion.getMajor() + "." + kotlinVersion.getMinor());
            builder.setModuleName(KotlinToolchain.MODULE_NAME);
            builder.setJdkHome(Path.of(System.getProperty("java.home")).toFile());
            builder.setProjectBaseDir(layout.projectDir().toFile());
            builder.setOutputBaseDir(Files.createDirectories(layout.buildDir()).toFile());
            builder.setClassOutputDir(Files.createDirectories(layout.classes()).toFile());
            builder.setJavaOutputDir(Files.createDirectories(layout.kspJavaOutput()).toFile());
            builder.setKotlinOutputDir(Files.createDirectories(layout.kspKotlinOutput()).toFile());
            builder.setResourceOutputDir(Files.createDirectories(layout.kspResourceOutput()).toFile());
            builder.setCachesDir(Files.createDirectories(layout.kspCachesDir()).toFile());
            builder.setSourceRoots(scenarioDescriptor.sources().toList());
            builder.setProcessorOptions(scenarioDescriptor.options().toMap());
            builder.setLibraries(List.copyOf(libraries));

            return new KotlinSymbolProcessing(
                    builder.build(),
                    processorProviders,
                    new KspSlf4jLogger(logCollector)
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup KSP", e);
        }
    }
}
