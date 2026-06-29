package com.qualityminds.lazyval.testkit.internal.toolchain.java;

import com.qualityminds.lazyval.testkit.dependencies.Dependency;
import com.qualityminds.lazyval.testkit.scenarios.Scenario;
import org.eclipse.collections.api.list.ImmutableList;

import javax.annotation.processing.Processor;
import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.eclipse.collections.impl.collector.Collectors2.toImmutableList;


/**
 * Single-step Java toolchain: javac runs annotation processing and compilation in one pass.
 * <p>
 * Counterpart to {@link com.qualityminds.lazyval.testkit.internal.toolchain.kotlin.KotlinToolchain},
 * but simpler because annotation processors run in-process and any error in generated Java surfaces
 * directly through the same {@code javac} invocation.
 * <p>
 * The toolchain layout — where javac writes sources vs. classes — is exposed via the static helpers
 * {@link #sourceOutputDir(Path)} and {@link #classOutputDir(Path)} so that consumers (the testkit's
 * collectors and its public path helpers) share one source of truth instead of duplicating string
 * literals.
 */
public class JavaToolchain implements AutoCloseable {

    /**
     * Root for annotation-processor-emitted Java sources ({@code <projectDir>/build/generated/}).
     * Maps to {@link StandardLocation#SOURCE_OUTPUT}.
     */
    public static Path sourceOutputDir(Path projectDir) {
        return projectDir.resolve("build/generated");
    }

    /**
     * Root for compiled classes <em>and</em> any non-source artifacts emitted by
     * {@code Filer.createResource(StandardLocation.CLASS_OUTPUT, ...)} — typically
     * {@code <projectDir>/build/classes/}. The toolchain filters {@code *.class} files out of the
     * Result's resource list, so callers using {@code generatedResources} see only the
     * processor-emitted artifacts.
     */
    public static Path classOutputDir(Path projectDir) {
        return projectDir.resolve("build/classes");
    }

    private final JavaCompiler.CompilationTask task;
    private final LoggingDiagnosticsCollector diagnostics;
    private final Path sourceOutputDir;
    private final Path classOutputDir;
    // Both owned by this toolchain; released in close(). The file manager holds OS handles to jars
    // on the classpath; the classloader is built from the URLs handed to the constructor so that no
    // external caller can supply a borrowed classloader we would then be responsible for closing.
    private final StandardJavaFileManager fileManager;
    private final URLClassLoader processorClassLoader;

    private JavaToolchain(JavaCompiler.CompilationTask task,
                          LoggingDiagnosticsCollector diagnostics,
                          Path sourceOutputDir,
                          Path classOutputDir,
                          StandardJavaFileManager fileManager,
                          URL[] processorClasspathUrls) {
        this.task = task;
        this.diagnostics = diagnostics;
        this.sourceOutputDir = sourceOutputDir;
        this.classOutputDir = classOutputDir;
        this.fileManager = fileManager;
        // Load processors dynamically so the testkit doesn't depend on the processor module — that
        // would create a cycle, since the processor's own tests use the testkit.
        this.processorClassLoader = new URLClassLoader(processorClasspathUrls, JavaToolchain.class.getClassLoader());
        var processors = ServiceLoader.load(Processor.class, processorClassLoader);
        var processorList = new ArrayList<Processor>();
        processors.forEach(processorList::add);
        task.setProcessors(processorList);
    }

    public Result run() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            // Set the context classloader so ServiceLoader inside the processor can find SPI providers
            Thread.currentThread().setContextClassLoader(processorClassLoader);

            boolean result = task.call();
            var generatedSources = walkFiles(sourceOutputDir, path -> true);
            // CLASS_OUTPUT also contains compiled .class files from the input sources; only the
            // non-class artifacts are processor-generated resources (META-INF/services, properties,
            // etc.). A generator that emits a .class file directly via Filer.createClassFile would
            // be missed here, but in practice no real generator does that.
            var generatedResources = walkFiles(classOutputDir, path -> !path.getFileName().toString().endsWith(".class"));
            return new Result(result, diagnostics.getDiagnostics(), generatedSources, generatedResources);
        } catch (Exception e) {
            throw new RuntimeException("Failed to collect generated files", e);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Override
    public void close() throws IOException {
        // Close the file manager first since it may hold handles to jars also referenced by the
        // classloader. Use try-with-resources so a failure on one still releases the other.
        try (fileManager; processorClassLoader) {
            // resources closed by try-with-resources
        }
    }

    private static TreeSet<Path> walkFiles(Path root, java.util.function.Predicate<Path> filter) throws IOException {
        if (!Files.isDirectory(root)) {
            return new TreeSet<>();
        }
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(p -> !Files.isDirectory(p))
                    .filter(filter)
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    public static JavaToolchain create(Path projectDir, Scenario.Descriptor scenarioDescriptor) {
        Objects.requireNonNull(projectDir);
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new LoggingDiagnosticsCollector();


        List<File> additionalClasspath = new ArrayList<>(CoreModuleDependency.RESOLVED_FILE.toSet());
        scenarioDescriptor.dependencies().stream().map(Dependency::resolve).forEach(x -> additionalClasspath.addAll(x.toSet()));

        var fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        try {
            return configure(projectDir, scenarioDescriptor, diagnostics, compiler, fileManager, additionalClasspath);
        } catch (Exception e) {
            // Ownership of the file manager only transfers to JavaToolchain on success; close it
            // here so a failure during setup doesn't leak file handles.
            try {
                fileManager.close();
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw new RuntimeException("Failed to setup compiler task", e);
        }
    }

    private static JavaToolchain configure(Path projectDir,
                                           Scenario.Descriptor scenarioDescriptor,
                                           LoggingDiagnosticsCollector diagnostics,
                                           JavaCompiler compiler,
                                           StandardJavaFileManager fileManager,
                                           List<File> additionalClasspath) throws IOException {
        var outputDir = Files.createDirectories(classOutputDir(projectDir));
        var generatedSourcesDir = Files.createDirectories(sourceOutputDir(projectDir));
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(outputDir.toFile()));
        fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, List.of(generatedSourcesDir.toFile()));

        if (!additionalClasspath.isEmpty()) {
            fileManager.setLocation(StandardLocation.CLASS_PATH, additionalClasspath);
        }

        var compilationUnits = fileManager.getJavaFileObjects(scenarioDescriptor.sources().toArray(new File[]{}));

        var processorOptions = scenarioDescriptor.options()
                .keyValuesView()
                .collect(pair -> "-A" + pair.getOne() + "=" + pair.getTwo());

        var task = compiler.getTask(null, fileManager, diagnostics, processorOptions, null, compilationUnits);

        URL[] processorClasspathUrls = additionalClasspath.stream()
                .map(file -> {
                    try {
                        return file.toURI().toURL();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toArray(URL[]::new);

        var sourceOutputLocation = fileManager.getLocation(StandardLocation.SOURCE_OUTPUT);
        var sourceOutputDir = sourceOutputLocation.iterator().next().toPath();
        var classOutputLocation = fileManager.getLocation(StandardLocation.CLASS_OUTPUT);
        var classOutputDir = classOutputLocation.iterator().next().toPath();
        return new JavaToolchain(task, diagnostics, sourceOutputDir, classOutputDir, fileManager, processorClasspathUrls);
    }

    /**
     * Result of a {@link JavaToolchain} run.
     * <p>
     * Counterpart to {@link com.qualityminds.lazyval.testkit.internal.toolchain.kotlin.KotlinToolchain.Result}.
     * Because javac is a single-step tool whose success model is binary, the outcome is a plain
     * {@link #taskResult()} boolean rather than a per-step enum map. The structured detail lives in
     * {@link #diagnostics()}.
     *
     * @param taskResult         whether the task completed successfully
     * @param diagnostics        the list of diagnostics that occurred during compilation, use
     *                           {@link #getErrors()} and {@link #getWarnings()} to extract specific kinds
     *                           as English strings
     * @param generatedSources   Java source files emitted by the annotation processor under
     *                           {@code SOURCE_OUTPUT} (typically {@code build/generated/})
     * @param generatedResources non-class artifacts emitted by the annotation processor under
     *                           {@code CLASS_OUTPUT} (typically {@code build/classes/}); excludes the
     *                           {@code .class} files produced by compilation of the input sources
     */
    public record Result(boolean taskResult,
                         List<Diagnostic<? extends JavaFileObject>> diagnostics,
                         SortedSet<Path> generatedSources,
                         SortedSet<Path> generatedResources) {

        public boolean isSuccessful() {
            return taskResult;
        }

        public ImmutableList<String> getErrors() {
            return diagnostics.stream()
                    .filter(it -> it.getKind() == Diagnostic.Kind.ERROR)
                    .map(s -> s.getMessage(Locale.ENGLISH))
                    .collect(toImmutableList());
        }

        public ImmutableList<String> getWarnings() {
            return diagnostics.stream()
                    .filter(it -> it.getKind() == Diagnostic.Kind.WARNING || it.getKind() == Diagnostic.Kind.MANDATORY_WARNING)
                    .map(s -> s.getMessage(Locale.ENGLISH))
                    .collect(toImmutableList());
        }

        /** True when the run produced neither sources nor resources. */
        public boolean generatedNoFiles() {
            return generatedSources.isEmpty() && generatedResources.isEmpty();
        }
    }
}
