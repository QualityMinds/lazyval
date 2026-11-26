package com.qualityminds.lazyval.testkit.internal.toolchain;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Compiles a scenario's library sources ahead of the scenario itself, into classes that both toolchains put on
 * the classpath the way they would the jar of another module. That is the shape of a type listed in
 * {@code @LazyvalConfiguration(externalTypes = ...)}: compiled elsewhere, and seen by the processor as class
 * files rather than as sources.
 * <p>
 * Compiled with javac and annotation processing switched off, since the library stands for a module that was
 * built on its own: nothing the processors under test generate may end up in it. A library that does not compile
 * is a broken fixture rather than a test result, so it fails the run instead of turning into one.
 */
public final class JavaLibrary {

    private JavaLibrary() {
    }

    /**
     * Compiles the given sources with the given classpath to the output-directory.
     * @param sources   the library's Java sources; empty for a scenario without a library
     * @param classpath what the library compiles against
     * @param outputDir where its classes go
     * @return the classpath entries the library adds: {@code outputDir}, or none for an empty library
     * @throws IllegalStateException if the library does not compile
     */
    public static List<File> compile(Collection<File> sources, List<File> classpath, Path outputDir) {
        if (sources.isEmpty()) {
            return List.of();
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(Files.createDirectories(outputDir).toFile()));
            fileManager.setLocation(StandardLocation.CLASS_PATH, classpath);
            var compilationUnits = fileManager.getJavaFileObjectsFromFiles(sources);
            var compiled = compiler.getTask(null, fileManager, diagnostics, List.of("-proc:none"), null, compilationUnits)
                    .call();
            if (!Boolean.TRUE.equals(compiled)) {
                var errors = diagnostics.getDiagnostics().stream()
                        .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                        .map(diagnostic -> diagnostic.getMessage(Locale.ROOT))
                        .collect(Collectors.joining("; "));
                throw new IllegalStateException("The scenario's library does not compile: " + errors);
            }
            return List.of(outputDir.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
