package com.qualityminds.lazyval.ksp.internal

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.qualityminds.lazyval.LazyvalConfiguration
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import java.util.*

internal class LazyvalKspEnvironment(
    private val environment: SymbolProcessorEnvironment,
    private val resolver: Resolver
) {

    companion object {
        const val DISABLED_GENERATORS: String = "lazyval.generators.disable"
        const val BASE_PACKAGE: String = "lazyval.generators.basePackage"
        private const val NO_GENERATION_WARNING = "None of the required classes are available on the classpath! Lazyval will not generate any sources."
    }

    private val logger: KSPLogger = environment.logger

    fun info(message: String) {
        logger.info("Lazyval: $message")
    }

    fun warn(message: String) {
        logger.warn("Lazyval: $message")
    }

    fun warn(symbol: KSNode, message: String) {
        logger.warn("Lazyval: $message", symbol)
    }

    fun warnMissingClasspath() {
        warn(NO_GENERATION_WARNING)
    }

    fun error(message: String) {
        logger.error("Lazyval: $message")
    }

    fun error(symbol: KSNode, message: String) {
        logger.error("Lazyval: $message", symbol)
    }

    fun createContext(fallback: ValidatedKspGeneratorElement): Generator.Context {
        return object : Generator.Context {
            override fun isOnClasspath(fqcn: String): Boolean {
                return isClassAvailable(fqcn)
            }

            override fun getSetting(key: String): String? {
                return environment.options[key]
            }

            override fun generatorPackage(overridePackageOptionKey: String, defaultLayer: String?): String {
                return getSetting(overridePackageOptionKey)
                    ?: getSetting(BASE_PACKAGE).let{ bp -> if (defaultLayer != null) "$bp.$defaultLayer" else bp }
                    ?: run {
                        val fallbackPackage = fallback.element.packageName.asString()
                        warn("Neither configuration for '$BASE_PACKAGE' nor '$overridePackageOptionKey' is set. Falling back to package of first element: '$fallbackPackage'")
                        return fallbackPackage
                    }
            }

            override fun logInfo(generator: Generator, message: String) {
                info(" [${generator.generatorId()}] $message")
            }

            override fun logWarning(generator: Generator, message: String) {
                warn(" [${generator.generatorId()}] $message")
            }

            override fun logWarning(
                generator: Generator,
                element: KSNode,
                message: String
            ) {
                warn(element, " [${generator.generatorId()}] $message")
            }

            override fun logError(generator: Generator, message: String) {
                error(" [${generator.generatorId()}] $message")
            }

            override fun logError(
                generator: Generator,
                element: KSNode,
                message: String
            ) {
                error(element, " [${generator.generatorId()}] $message")
            }
        }
    }

    /**
     * Checks whether a class with the given [fqn] is available on the classpath.
     */
    fun isClassAvailable(fqn: String): Boolean {
        if (fqn.isBlank()) {
            warn("$fqn is not on classpath.")
            return false
        }
        return resolver.getClassDeclarationByName(resolver.getKSNameFromString(fqn)) != null
    }

    fun disabledGenerators(): List<String> = Arrays.stream(
        environment.options
            .getOrDefault(DISABLED_GENERATORS, "")
            .split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        .map { obj: String? -> obj!!.trim { it <= ' ' } }
        .filter { s: String? -> !s!!.isEmpty() }
        .toList()

    /**
     * Reads [LazyvalConfiguration.externalTypes] from the current round's `package-info.java`.
     *
     * - Returns an empty list when no holder is present.
     * - Reports a compile error and returns an empty list when more than one holder is present.
     * - Skips and reports a compile error for any listed type that belongs to the current
     *   compilation unit (such types must use [com.qualityminds.lazyval.LazyValue]).
     */
    fun configuredValues(): List<KSClassDeclaration> {
        val annotationFqn = LazyvalConfiguration::class.qualifiedName ?: return emptyList()
        val holders = resolver.getSymbolsWithAnnotation(annotationFqn).toList()
        if (holders.isEmpty()) {
            return emptyList()
        }
        if (holders.size > 1) {
            holders.drop(1).forEach { extra ->
                error(extra, "Only one @LazyvalConfiguration is allowed per compilation unit.")
            }
            return emptyList()
        }

        val holder = holders.first()
        val annotation = holder.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == annotationFqn
        } ?: return emptyList()

        @Suppress("UNCHECKED_CAST")
        val externalTypes = annotation.arguments
            .firstOrNull { it.name?.asString() == "externalTypes" }
            ?.value as? List<KSType>
            ?: return emptyList()

        val localFqns = resolver.getAllFiles()
            .flatMap { it.declarations }
            .filterIsInstance<KSClassDeclaration>()
            .mapNotNull { it.qualifiedName?.asString() }
            .toSet()

        return externalTypes.mapNotNull { ksType ->
            val decl = ksType.declaration as? KSClassDeclaration ?: return@mapNotNull null
            val fqn = decl.qualifiedName?.asString()
            if (fqn != null && fqn in localFqns) {
                error(holder, "Type '$fqn' listed in @LazyvalConfiguration.externalTypes belongs to the current compilation unit. Annotate it with @LazyValue directly.")
                null
            } else {
                decl
            }
        }
    }


}