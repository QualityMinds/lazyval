package com.qualityminds.lazyval.testkit.internal.toolchain.kotlin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import java.util.Locale;
import java.util.Objects;

/**
 * DiagnosticListener for javac which logs to SLF4J and forwards error/warning messages to the
 * shared {@link LogCollector} so they end up in the {@link ToolchainResult}.
 */
class LoggingDiagnosticsCollector<S> implements DiagnosticListener<S> {

    private static final Logger logger = LoggerFactory.getLogger(LoggingDiagnosticsCollector.class);
    private final LogCollector logCollector;

    LoggingDiagnosticsCollector(LogCollector logCollector) {
        this.logCollector = logCollector;
    }

    @Override
    public void report(Diagnostic<? extends S> diagnostic) {
        Objects.requireNonNull(diagnostic);
        var message = diagnostic.getMessage(Locale.ENGLISH);
        switch (diagnostic.getKind()) {
            case ERROR -> {
                logCollector.addError(message);
                logger.error(message);
            }
            case WARNING, MANDATORY_WARNING -> {
                logCollector.addWarning(message);
                logger.warn(message);
            }
            case NOTE -> logger.info(message);
            case OTHER -> logger.debug(message);
        }
    }
}
