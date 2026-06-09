package com.qualityminds.lazyval.testkit.internal.toolchain.kotlin;

import com.google.devtools.ksp.processing.KSPLogger;
import com.google.devtools.ksp.symbol.KSNode;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logger for KSP which routes logging to SLF4J
 */
class KspSlf4jLogger implements KSPLogger {

    private final Logger logger;
    private final LogCollector logCollector;


    KspSlf4jLogger(LogCollector logCollector) {
        this("KSP", logCollector);
    }

    KspSlf4jLogger(String loggerName, LogCollector logCollector) {
        this.logger = LoggerFactory.getLogger(loggerName);
        this.logCollector = logCollector;
    }

    @Override
    public void error(String message, @Nullable KSNode symbol) {
        logCollector.addError(message);
        if (symbol != null) {
            logger.error("{} [{}]", message, symbol.getLocation());
        } else {
            logger.error(message);
        }
    }

    @Override
    public void exception(Throwable e) {
        logger.error("Exception occurred", e);
    }

    @Override
    public void info(String message, @Nullable KSNode symbol) {
        if (symbol != null) {
            logger.info("{} [{}]", message, symbol.getLocation());
        } else {
            logger.info(message);
        }
    }

    @Override
    public void logging(String message, @Nullable KSNode symbol) {
        if (symbol != null) {
            logger.debug("{} [{}]", message, symbol.getLocation());
        } else {
            logger.debug(message);
        }
    }

    @Override
    public void warn(String message, @Nullable KSNode symbol) {
        logCollector.addWarning(message);
        if (symbol != null) {
            logger.warn("{} [{}]", message, symbol.getLocation());
        } else {
            logger.warn(message);
        }
    }
}
