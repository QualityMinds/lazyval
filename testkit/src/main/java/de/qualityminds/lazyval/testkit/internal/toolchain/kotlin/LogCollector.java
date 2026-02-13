package de.qualityminds.lazyval.testkit.internal.toolchain.kotlin;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures Log-Messages (only error/warning) to have access in tests
 */
public class LogCollector {

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public ImmutableList<String> getErrors() {
        return Lists.immutable.ofAll(errors);
    }

    public ImmutableList<String> getWarnings() {
        return Lists.immutable.ofAll(warnings);
    }

    public void addWarning(String message) {
        warnings.add(message);
    }

    public void addError(String message) {
        errors.add(message);
    }
}
