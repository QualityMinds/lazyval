package com.qualityminds.lazyval.testkit.internal.toolchain.java;

import org.eclipse.collections.api.list.ImmutableList;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;

import static org.eclipse.collections.impl.collector.Collectors2.toImmutableList;


public record CompilerResult(boolean taskResult, List<Diagnostic<? extends JavaFileObject>> diagnostics,
                             SortedSet<Path> generatedFiles) {

    public List<Diagnostic<? extends JavaFileObject>> getErrors() {
        return diagnostics.stream()
                .filter(it -> it.getKind() == Diagnostic.Kind.ERROR)
                .toList();
    }

    public ImmutableList<Diagnostic<? extends JavaFileObject>> getWarnings() {
        return diagnostics.stream()
                .filter(it -> it.getKind() == Diagnostic.Kind.WARNING || it.getKind() == Diagnostic.Kind.MANDATORY_WARNING)
                .collect(toImmutableList());
    }

    public boolean wasNoGenerationWarning() {
        return getWarnings().stream()
                .anyMatch(it -> it.getMessage(Locale.ENGLISH).equals("None of the required classes are available on the classpath! Lazyval will not generate any sources."));
    }

    public boolean wasObjectNotFinalWarning() {
        return getWarnings().stream()
                .anyMatch(it -> it.getMessage(Locale.ENGLISH).equals("Value Types should not be extendable, hence the class should be final."));
    }

    public boolean wasValueNotFinalWarning() {
        return getWarnings().stream()
                .anyMatch(it -> it.getMessage(Locale.ENGLISH).equals("Value Types should be immutable, hence the wrapped field should be final."));
    }

    public boolean generatedFile(String s) {
        return generatedFiles.stream().anyMatch(it -> it.getFileName().toString().equals(s));
    }

    public boolean generatedNoFiles() {
        return generatedFiles.isEmpty();
    }
}
