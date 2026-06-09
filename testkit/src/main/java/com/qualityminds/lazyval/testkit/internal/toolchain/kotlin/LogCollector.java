package com.qualityminds.lazyval.testkit.internal.toolchain.kotlin;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures Log-Messages (only error/warning) to have access in tests
 */
class LogCollector {

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    ImmutableList<String> getErrors() {
        return Lists.immutable.ofAll(errors);
    }

    ImmutableList<String> getWarnings() {
        return Lists.immutable.ofAll(warnings);
    }

    void addWarning(String message) {
        warnings.add(message);
    }

    void addError(String message) {
        errors.add(message);
    }
}
