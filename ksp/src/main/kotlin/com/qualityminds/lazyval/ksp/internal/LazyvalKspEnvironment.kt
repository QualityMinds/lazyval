@file:Suppress("TooManyFunctions")

package com.qualityminds.lazyval.ksp.internal

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.qualityminds.lazyval.ksp.spi.WrappedProperty
import java.util.*

internal class LazyvalKspEnvironment(
    private val environment: SymbolProcessorEnvironment,
    private val resolver: Resolver
) {

    companion object {
        const val DISABLED_GENERATORS: String = "lazyval.generators.disable"
        const val BASE_PACKAGE: String = "lazyval.generators.basePackage"
        const val CONFIGURED_VALUES: String = "lazyval.values"
        private const val NO_GENERATION_WARNING = "None of the required classes are available on the classpath! Lazyval will not generate any sources."
        private const val NOT_FINAL_CLASS_WARNING = "Value Types should not be extendable, hence the class should be final."
        private const val NOT_FINAL_VALUE_WARNING = "Value Types should be immutable, hence the wrapped property should be final (val)."


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

    fun configuredValues(): List<KSClassDeclaration> = environment.options
            .getOrDefault(CONFIGURED_VALUES, "")
            .split(",".toRegex())
            .dropLastWhile { it.isEmpty() }
            .map { obj: String? -> obj!!.trim { it <= ' ' } }
            .filter { s: String? -> !s!!.isEmpty() }
            .mapNotNull { fqn ->
                val type = resolver.getClassDeclarationByName(resolver.getKSNameFromString(fqn))
                if (type == null) {
                    error(String.format("Configured value '%s' could not be resolved.", fqn))
                }
                type
            }


    fun validateElement(classDeclaration: KSClassDeclaration): ValidatedKspGeneratorElement? {
        // KSPs validation is to strict, it won't allow the Java Annotation on a Kotlin class
//        if (!classDeclaration.validate()) {
//            environment.logger.error("Invalid Kotlin class declaration", classDeclaration)
//            return null
//        }

        return when (classDeclaration.classKind) {
            ClassKind.CLASS -> validateClass(classDeclaration)
            else -> {
                error(classDeclaration, "Only classes and data classes are supported by Lazyval.")
                null
            }
        }
    }

    private fun validateClass(classDeclaration: KSClassDeclaration): ValidatedKspGeneratorElement? {
        var valid = true

        if (Modifier.ABSTRACT in classDeclaration.modifiers) {
            error(classDeclaration, "Abstract class is not a valid ValueType.")
            valid = false
        }

        if (Modifier.VALUE in classDeclaration.modifiers) {
            error(classDeclaration, "value class is not supported by Lazyval.")
            valid = false
        }

        // Find properties or their corresponding accessor methods
        val publicProperties = classDeclaration.getAllProperties().toList()
        val propertyAccessorPairs = findPropertyAccessorPairs(classDeclaration)

        if (propertyAccessorPairs.size > 1 || publicProperties.size > 1) {
            error(classDeclaration, "Not a simple ValueType. Lazyval only supports classes with one property.")
            valid = false
        } else if (propertyAccessorPairs.isEmpty() && publicProperties.isEmpty()) {
            error(classDeclaration, "No accessible properties found. Lazyval requires the ValueType to have exactly one accessible property.")
            return null
        }

        if(publicProperties.first().type.resolve().isMarkedNullable) {
            error(publicProperties.first(), "Wrapped type must not be nullable. Please use a non-nullable type.")
            valid = false
        }

        val valueProperty = publicProperties.firstOrNull() ?: propertyAccessorPairs.first().first
        val accessorMethod = propertyAccessorPairs.firstOrNull()?.second

        // Find factory methods in a companion object
        val factoryMethods = findFactoryMethods(classDeclaration, valueProperty.type.resolve())
        if (factoryMethods.size > 1) {
            val functionNames = factoryMethods.joinToString(", ") { it.simpleName.asString() }
            error(classDeclaration, "Multiple matching factory methods with the same signature found. Please check functions $functionNames")
            valid = false
        }

        val factoryMethod = factoryMethods.firstOrNull()

        return if (valid) {
            if (Modifier.OPEN in classDeclaration.modifiers) {
                warn(classDeclaration, NOT_FINAL_CLASS_WARNING)
            }
            if (valueProperty.isMutable) {
                warn(valueProperty, NOT_FINAL_VALUE_WARNING)
            }
            ValidatedKspGeneratorElement(
                classDeclaration,
                WrappedProperty(valueProperty),
                factoryMethod,
                accessorMethod)
        } else {
            null
        }
    }

    private fun findPropertyAccessorPairs(classDeclaration: KSClassDeclaration): List<Pair<KSPropertyDeclaration, KSFunctionDeclaration?>> {
        val properties = classDeclaration.getAllProperties()
            .filter { property ->
                !property.isStatic() &&
                        property.getter != null &&
                        property.hasBackingField
            }
            .toList()

        val methods = classDeclaration.getAllFunctions()
            .filter { function ->
                !function.isStatic() &&
                        function.parameters.isEmpty() &&
                        function.returnType != null &&
                        function.returnType!!.resolve().toString() != "kotlin.Unit"
            }
            .toList()

        return properties.mapNotNull { property ->
            // Look for accessor method that matches the property type
            val accessor = methods.firstOrNull { method ->
                method.returnType?.resolve() == property.type.resolve() &&
                        (method.simpleName.asString() == property.simpleName.asString() ||
                                method.simpleName.asString() == "${property.simpleName.asString()}()")
            }

            // For data classes, the accessor might be the property itself
            // For regular classes an explicit accessor method is needed
            if (accessor != null || (classDeclaration.modifiers.contains(Modifier.DATA) && !property.isPrivate())) {
                Pair(property, accessor)
            } else {
                null
            }
        }
    }

    private fun findFactoryMethods(classDeclaration: KSClassDeclaration, wrappedType: KSType): List<KSFunctionDeclaration> {
        val companionObject = classDeclaration.declarations
            .filterIsInstance<KSClassDeclaration>()
            .firstOrNull { it.isCompanionObject }
            ?: return emptyList()

        return companionObject.declarations
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { function ->
                // Check basic structure first
                if (function.parameters.size != 1) return@filter false

                // Check if return type matches (including nullable variants)
                val returnType = function.returnType?.resolve() ?: return@filter false
                val returnTypeMatches = returnType == classDeclaration.asStarProjectedType() ||
                        (returnType.isMarkedNullable && returnType.makeNotNullable() == classDeclaration.asStarProjectedType())
                if (!returnTypeMatches) return@filter false

                // Check if parameter type matches (including nullable variants)
                val paramType = function.parameters[0].type.resolve()
                val paramTypeMatches = paramType == wrappedType ||
                        (paramType.isMarkedNullable && paramType.makeNotNullable() == wrappedType)

                paramTypeMatches
            }
            .toList()
    }

    private fun KSPropertyDeclaration.isStatic(): Boolean {
        return Modifier.CONST in modifiers ||
                parent is KSClassDeclaration && (parent as KSClassDeclaration).isCompanionObject
    }

    private fun KSFunctionDeclaration.isStatic(): Boolean {
        return parent is KSClassDeclaration && (parent as KSClassDeclaration).isCompanionObject
    }

    private fun KSPropertyDeclaration.isPrivate(): Boolean {
        return Modifier.PRIVATE in modifiers
    }
}