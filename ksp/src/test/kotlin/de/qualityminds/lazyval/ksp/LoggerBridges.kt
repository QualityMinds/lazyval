package de.qualityminds.lazyval.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import org.slf4j.LoggerFactory
import org.jetbrains.kotlin.buildtools.api.KotlinLogger

/**
 * Logger for KSP which routes logging to SLF4J
 */
class KspSlf4jLogger(loggerName: String = "KSP") : KSPLogger {
    private val logger = LoggerFactory.getLogger(loggerName)

    override fun error(message: String, symbol: KSNode?) {
        if (symbol != null) {
            logger.error("$message [${symbol.location}]")
        } else {
            logger.error(message)
        }
    }

    override fun exception(e: Throwable) {
        logger.error("Exception occurred", e)
    }

    override fun info(message: String, symbol: KSNode?) {
        if (symbol != null) {
            logger.info("$message [${symbol.location}]")
        } else {
            logger.info(message)
        }
    }

    override fun logging(message: String, symbol: KSNode?) {
        if (symbol != null) {
            logger.debug("$message [${symbol.location}]")
        } else {
            logger.debug(message)
        }
    }

    override fun warn(message: String, symbol: KSNode?) {
        if (symbol != null) {
            logger.warn("$message [${symbol.location}]")
        } else {
            logger.warn(message)
        }
    }
}

/**
 * Logger for Kotlin Compiler which routes logging to SLF4J
 */
class KotlinCompilerSlf4jLogger(loggerName: String = "KotlinCompiler") : KotlinLogger {

    private val logger = LoggerFactory.getLogger(loggerName)

    override val isDebugEnabled: Boolean
        get() = logger.isDebugEnabled

    override fun debug(msg: String) {
        logger.debug(msg)
    }

    override fun error(msg: String, throwable: Throwable?) {
        logger.error(msg, throwable)
    }

    override fun info(msg: String) {
        logger.info(msg)
    }

    override fun lifecycle(msg: String) {
        logger.debug(msg)
    }

    override fun warn(msg: String, throwable: Throwable?) {
        logger.warn(msg, throwable)
    }
}