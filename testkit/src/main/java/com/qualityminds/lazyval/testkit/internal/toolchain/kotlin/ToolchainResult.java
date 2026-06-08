package com.qualityminds.lazyval.testkit.internal.toolchain.kotlin;

import org.eclipse.collections.api.list.ImmutableList;

import java.nio.file.Path;
import java.util.SortedSet;

/**
 * Result of the toolchain execution containing KSP, Kotlin and Java compilation results.
 */
public record ToolchainResult(boolean kspSuccess, boolean kotlinSuccess, boolean javacSuccess,
                              SortedSet<Path> generatedJavaFiles,
                              SortedSet<Path> generatedKotlinFiles,
                              ImmutableList<String> errors,
                              ImmutableList<String> warnings) {

    public boolean isSuccessful() {
        return kspSuccess && kotlinSuccess && javacSuccess;
    }

    public void printDebugMessages() {
        System.out.println("KSP Success: " + kspSuccess);
        System.out.println("Kotlin Success: " + kotlinSuccess);
        System.out.println("Javac Success: " + javacSuccess);
        System.out.println("Generated Java Files:");
        generatedJavaFiles.forEach(System.out::println);
        System.out.println("Generated Kotlin Files:");
        generatedKotlinFiles.forEach(System.out::println);
    }

    public boolean generatedJavaFile(String name) {
        return generatedJavaFiles.stream()
                .anyMatch(path -> path.getFileName().toString().equals(name));
    }

    public boolean generatedKotlinFile(String name) {
        return generatedKotlinFiles.stream()
                .anyMatch(path -> path.getFileName().toString().equals(name));
    }

    public boolean generatedNoFiles() {
        return generatedJavaFiles.isEmpty() && generatedKotlinFiles.isEmpty();
    }
}
