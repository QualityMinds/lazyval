package de.qualityminds.lazyval.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*

class LazyvalKspEnvironment(
    private val environment: SymbolProcessorEnvironment,
    private val resolver: Resolver
) {

    companion object {
        const val DISABLED_GENERATORS: String = "lazyval.disabledGenerators"
        private const val NO_GENERATION_WARNING = "None of the required classes are available on the classpath! Lazyval will not generate any sources."
        private const val NOT_FINAL_CLASS_WARNING = "Value Types should not be extendable, hence the class should be final."
        private const val NOT_FINAL_VALUE_WARNING = "Value Types should be immutable, hence the wrapped property should be final (val)."

        private val layerPackages = setOf("boundary", "control", "entity")

        fun extractRootPackage(classDeclaration: KSClassDeclaration): String {
            val packageParts = classDeclaration.packageName.asString().split(".")
            return packageParts.takeWhile { part ->
                !layerPackages.contains(part) && !part.first().isUpperCase()
            }.joinToString(".")
        }
    }

    private val logger: KSPLogger = environment.logger
    private val mapstructOnClasspath: Boolean = isClassAvailable("org.mapstruct.Mapper")
    private val jpaOnClasspath: Boolean = isClassAvailable("jakarta.persistence.AttributeConverter")

    fun info(message: String) {
        logger.info(message)
    }

    fun warn(message: String) {
        logger.warn(message)
    }

    fun warn(symbol: KSNode, message: String) {
        logger.warn(message, symbol)
    }

    fun warnMissingClasspath() {
        warn(NO_GENERATION_WARNING)
    }

    fun error(message: String) {
        logger.error(message)
    }

    fun error(symbol: KSNode, message: String) {
        logger.error(message, symbol)
    }

    internal fun isMapstructOnClasspath(): Boolean = mapstructOnClasspath

    internal fun isJpaOnClasspath(): Boolean = jpaOnClasspath

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

        val valueProperty = publicProperties.firstOrNull() ?: propertyAccessorPairs.first().first
        val accessorMethod = propertyAccessorPairs.firstOrNull()?.second

        // Find factory methods in a companion object
        val factoryMethods = findFactoryMethods(classDeclaration, valueProperty.type.resolve())
        if (factoryMethods.size > 1) {
            error(classDeclaration, "Multiple factory methods found. Lazyval supports only one factory method.")
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
            KspClassElement(classDeclaration, valueProperty, accessorMethod, factoryMethod)
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
                function.returnType?.resolve() == classDeclaration.asStarProjectedType() &&
                        function.parameters.size == 1 &&
                        function.parameters[0].type.resolve() == wrappedType
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