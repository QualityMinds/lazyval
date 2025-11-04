package de.qualityminds.lazyval.testkit.toolchain.java;

import de.qualityminds.lazyval.testkit.dependencies.Dependency;
import de.qualityminds.lazyval.testkit.scenarios.Scenario;

import javax.annotation.processing.Processor;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;


public class CompilerSetup {

    private final JavaCompiler.CompilationTask task;
    private final LoggingDiagnosticsCollector<JavaFileObject> diagnostics;
    private final Path sourceOutputDir;
    private final ClassLoader processorClassLoader;

    private CompilerSetup(JavaCompiler.CompilationTask task, LoggingDiagnosticsCollector<JavaFileObject> diagnostics, Path sourceOutputDir, ClassLoader processorClassLoader) {
        this.task = task;
        this.diagnostics = diagnostics;
        this.sourceOutputDir = sourceOutputDir;
        this.processorClassLoader = processorClassLoader;
    }

    public CompilerResult run() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            // Set the context classloader so ServiceLoader inside the processor can find SPI providers
            Thread.currentThread().setContextClassLoader(processorClassLoader);

            boolean result = task.call();
            return new CompilerResult(
                    result,
                    diagnostics.getDiagnostics(),
                    new TreeSet<>(Files.walk(sourceOutputDir).filter(p -> !Files.isDirectory(p)).toList())
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to collect generated files", e);
        }finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    public static CompilerSetup setupTask(Path projectDir, Scenario.Descriptor scenarioDescriptor) {
        Objects.requireNonNull(projectDir);
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new LoggingDiagnosticsCollector<JavaFileObject>();


        List<File> additionalClasspath = new ArrayList<>(CoreModuleDependency.RESOLVED_FILE.toSet());
        scenarioDescriptor.dependencies().stream().map(Dependency::resolve).forEach(x -> additionalClasspath.addAll(x.toSet()));

        var fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        try {
            var outputDir = Files.createDirectories(projectDir.resolve("build/classes"));
            var generatedSourcesDir = Files.createDirectories(projectDir.resolve("build/generated"));
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(outputDir.toFile()));
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, List.of(generatedSourcesDir.toFile()));

            if (!additionalClasspath.isEmpty()) {
                fileManager.setLocation(StandardLocation.CLASS_PATH, additionalClasspath);
            }

            var allSources = Stream.concat(
                    Stream.of(scenarioDescriptor.source()),
                    scenarioDescriptor.additionalSources().stream()
            ).toArray(File[]::new);
            var compilationUnits = fileManager.getJavaFileObjects(allSources);
            List<String> options = null;
            if (!scenarioDescriptor.disabledGenerators().isEmpty()) {
                options = List.of("-Alazyval.disabledGenerators=" + String.join(",", scenarioDescriptor.disabledGenerators()));
            }
            var task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);


            URL[] urls = additionalClasspath.stream()
                    .map(file -> {
                        try {
                            return file.toURI().toURL();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toArray(URL[]::new);
            // Load the LazyvalProcessor dynamically to avoid a project dependency on the processor because
            // then we would have a cycle processor <-> testkit (since we want to use the testkit in the processor tests)
            URLClassLoader processorClassLoader = new URLClassLoader(urls, CompilerSetup.class.getClassLoader());
            ServiceLoader<Processor> processors = ServiceLoader.load(Processor.class, processorClassLoader);
            List<Processor> processorList = new ArrayList<>();
            processors.forEach(processorList::add);
            task.setProcessors(processorList);

            var outputLocation = fileManager.getLocation(StandardLocation.SOURCE_OUTPUT);
            var sourceOutputDir = outputLocation.iterator().next().toPath();
            return new CompilerSetup(task, diagnostics, sourceOutputDir, processorClassLoader);
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup compiler task", e);
        }
    }
}
