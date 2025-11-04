
package de.qualityminds.lazyval.testkit.toolchain.java;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A simple DiagnosticListener which behaves the same as {@link javax.tools.DiagnosticCollector}, but also logs to SLF4J
 * for better tracing during testing.
 */
class LoggingDiagnosticsCollector<S> implements DiagnosticListener<S> {

    private static final Logger logger = LoggerFactory.getLogger(LoggingDiagnosticsCollector.class);
    private final List<Diagnostic<? extends S>> diagnostics =
            Collections.synchronizedList(new ArrayList<>());

    @Override
    public void report(Diagnostic<? extends S> diagnostic) {
        Objects.requireNonNull(diagnostic);
        diagnostics.add(diagnostic);
        switch (diagnostic.getKind()) {
            case ERROR -> logger.error(diagnostic.getMessage(Locale.ENGLISH));
            case WARNING, MANDATORY_WARNING -> logger.warn(diagnostic.getMessage(Locale.ENGLISH));
            case NOTE -> logger.info(diagnostic.getMessage(Locale.ENGLISH));
            case OTHER -> logger.debug(diagnostic.getMessage(Locale.ENGLISH));
        }
    }

    public List<Diagnostic<? extends S>> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }
}

