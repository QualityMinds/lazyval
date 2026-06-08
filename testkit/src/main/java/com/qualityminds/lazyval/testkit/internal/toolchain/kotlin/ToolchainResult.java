package com.qualityminds.lazyval.testkit.internal.toolchain.kotlin;

import org.eclipse.collections.api.list.ImmutableList;

import java.nio.file.Path;
import java.util.Map;
import java.util.SortedSet;

/**
 * Result of a {@link KotlinToolchain} run.
 * <p>
 * {@link #stepOutcomes()} records the outcome of every step that was started. Steps that never started
 * (because an earlier one failed) do not appear in the map; query via {@link #outcomeFor(Step)} which
 * defaults to {@link StepOutcome#SKIPPED} for such steps.
 */
public record ToolchainResult(Map<Step, StepOutcome> stepOutcomes,
                              SortedSet<Path> generatedJavaFiles,
                              SortedSet<Path> generatedKotlinFiles,
                              ImmutableList<String> errors,
                              ImmutableList<String> warnings) {

    public boolean isSuccessful() {
        return stepOutcomes.values().stream().allMatch(StepOutcome::isSuccessful);
    }

    public StepOutcome outcomeFor(Step step) {
        return stepOutcomes.getOrDefault(step, StepOutcome.SKIPPED);
    }

    public void printDebugMessages() {
        stepOutcomes.forEach((step, outcome) -> System.out.println(step + ": " + outcome));
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
