package com.qualityminds.lazyval.ksp.internal

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
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
        const val SUPERSEDE_ENABLED: String = "lazyval.generators.supersede"
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

            override fun inspectClass(fqcn: String): Generator.Context.ClassInspection? {
                if (fqcn.isBlank()) {
                    return null
                }
                val declaration = resolver.getClassDeclarationByName(resolver.getKSNameFromString(fqcn))
                    ?: return null
                return KspClassInspection(declaration)
            }

            override fun generatorPackage(overridePackageOptionKey: String, defaultLayer: String?): String {
                val config = PackageLookup.DefaultConfig.of(
                        getSetting(BASE_PACKAGE),
                        defaultLayer
                    )

                return PackageLookup.computePackage(config, getSetting(overridePackageOptionKey)) {
                        val fallbackPackage = fallback.element.packageName.asString()
                        warn("Neither configuration for '$BASE_PACKAGE' nor '$overridePackageOptionKey' is set. Falling back to package of first element: '$fallbackPackage'")
                        return@computePackage fallbackPackage
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
     * The name [function] actually carries in the bytecode, which is what generated Java has to spell.
     * Differs from the Kotlin name whenever `@JvmName` renames it, and carries a `$module` suffix when
     * the function is `internal`.
     *
     * `null` signals a resolution failure rather than "no JVM name" — callers fall back to the Kotlin
     * name, which is what Lazyval assumed before the JVM name was consulted at all.
     */
    @OptIn(KspExperimental::class)
    fun jvmName(function: KSFunctionDeclaration): String? = resolver.getJvmName(function)

    /**
     * The name [accessor] actually carries in the bytecode. Mirrors [jvmName] for a property's getter,
     * where `@get:JvmName` plays the same role `@JvmName` does on a function.
     */
    @OptIn(KspExperimental::class)
    fun jvmName(accessor: KSPropertyAccessor): String? = resolver.getJvmName(accessor)

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

    fun isSupersedeEnabled(): Boolean {
        return environment.options[SUPERSEDE_ENABLED]?.toBoolean() ?: true
    }

    /**
     * Reads [LazyvalConfiguration.externalTypes] from the current round.
     *
     * - Returns an empty list when no holder is present.
     * - Reports a compile error and returns an empty list when more than one holder is present.
     * - Skips and reports a compile error for any listed type that belongs to the current
     *   compilation unit (such types must use [com.qualityminds.lazyval.LazyValue]).
     * - Deduplicates types listed more than once and reports a warning so the user can clean up
     *   the configuration; the type is still processed once.
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

        val seenFqns = mutableSetOf<String>()
        return externalTypes.mapNotNull { ksType ->
            val decl = ksType.declaration as? KSClassDeclaration ?: return@mapNotNull null
            val fqn = decl.qualifiedName?.asString()
            when {
                fqn != null && fqn in localFqns -> {
                    error(holder, "Type '$fqn' listed in @LazyvalConfiguration.externalTypes belongs to the current compilation unit. Annotate it with @LazyValue directly.")
                    null
                }
                fqn != null && !seenFqns.add(fqn) -> {
                    warn(holder, "Duplicate type '$fqn' in @LazyvalConfiguration.externalTypes. It will only be processed once.")
                    null
                }
                else -> decl
            }
        }
    }
}

private class KspClassInspection(private val declaration: KSClassDeclaration) : Generator.Context.ClassInspection {

    override fun isAccessibleFrom(packageName: String): Boolean =
        isAccessible(declaration, declaration.getVisibility(), packageName)

    override fun hasAccessibleNoArgConstructor(packageName: String): Boolean =
        declaration.getConstructors().any { ctor ->
            ctor.parameters.isEmpty() && isAccessible(declaration, ctor.getVisibility(), packageName)
        }

    override fun isAssignableTo(supertypeFqn: String): Boolean {
        val visited = HashSet<String>()
        val queue: ArrayDeque<KSClassDeclaration> = ArrayDeque()
        queue += declaration
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val fqn = current.qualifiedName?.asString() ?: continue
            if (!visited.add(fqn)) continue
            if (fqn == supertypeFqn) return true
            for (parent in current.superTypes) {
                val parentDecl = parent.resolve().declaration as? KSClassDeclaration ?: continue
                queue += parentDecl
            }
        }
        return false
    }

    override fun hasAnnotation(annotationFqn: String): Boolean {
        return declaration.annotations.any { anno ->
            anno.annotationType.resolve().declaration.qualifiedName?.asString() == annotationFqn
        }
    }

    private fun isAccessible(decl: KSDeclaration, visibility: Visibility, packageName: String): Boolean =
        when (visibility) {
            Visibility.PUBLIC -> true
            Visibility.INTERNAL -> isFromCurrentModule(decl)
            Visibility.JAVA_PACKAGE -> declaration.packageName.asString() == packageName
            else -> false
        }

    private fun isFromCurrentModule(decl: KSDeclaration): Boolean =
        decl.origin == Origin.KOTLIN || decl.origin == Origin.JAVA
}