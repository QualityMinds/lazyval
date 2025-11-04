package de.qualityminds.lazyval.testkit.toolchain.kotlin;

import org.jetbrains.kotlin.buildtools.api.KotlinLogger;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * Logger for Kotlin Compiler which routes logging to SLF4J
 */
class KotlinCompilerSlf4jLogger implements KotlinLogger {

    private final Logger logger;
    private final LogCollector logCollector;

    // Compiler logs start with the filename. This is not needed for tests, so it is removed with this pattern
    private static final Pattern fileReplacer = Pattern.compile("file:[^\\s]+\\s*");

    public KotlinCompilerSlf4jLogger(LogCollector logCollector) {
        this("KotlinCompiler", logCollector);
    }

    public KotlinCompilerSlf4jLogger(String loggerName, LogCollector logCollector) {
        this.logger = LoggerFactory.getLogger(loggerName);
        this.logCollector = logCollector;
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public void debug(String msg) {
        logger.debug(msg);
    }

    @Override
    public void error(String msg, @Nullable Throwable throwable) {
        logCollector.addError(fileReplacer.matcher(msg).replaceAll(""));
        logger.error(msg, throwable);
    }

    @Override
    public void info(String msg) {
        logger.info(msg);
    }

    @Override
    public void lifecycle(String msg) {
        logger.debug(msg);
    }

    @Override
    public void warn(String msg, @Nullable Throwable throwable) {
        logCollector.addWarning(fileReplacer.matcher(msg).replaceAll(""));
        logger.warn(msg, throwable);
    }
}
