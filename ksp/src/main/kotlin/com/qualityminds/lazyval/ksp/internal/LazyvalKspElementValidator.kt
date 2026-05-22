package com.qualityminds.lazyval.ksp.internal

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.qualityminds.lazyval.ksp.spi.WrappedProperty

/**
 * Validates a [KSClassDeclaration] against Lazyval's value-type contract and returns a
 * [ValidatedKspGeneratorElement] ready for code generation. Errors and warnings are reported
 * via the supplied [LazyvalKspEnvironment].
 */
internal class LazyvalKspElementValidator(private val environment: LazyvalKspEnvironment) {

    private companion object {
        const val NOT_FINAL_CLASS_WARNING =
            "Value Types should not be extendable, hence the class should be final."
        const val NOT_FINAL_VALUE_WARNING =
            "Value Types should be immutable, hence the wrapped property should be final (val)."
    }

    fun validate(classDeclaration: KSClassDeclaration): ValidatedKspGeneratorElement? {
        return when (classDeclaration.classKind) {
            ClassKind.CLASS -> validateClass(classDeclaration)
            else -> {
                environment.error(classDeclaration, "Only classes and data classes are supported by Lazyval.")
                null
            }
        }
    }

    private fun validateClass(classDeclaration: KSClassDeclaration): ValidatedKspGeneratorElement? {
        var valid = true

        if (Modifier.ABSTRACT in classDeclaration.modifiers) {
            environment.error(classDeclaration, "Abstract class is not a valid ValueType.")
            valid = false
        }

        if (Modifier.VALUE in classDeclaration.modifiers) {
            environment.error(classDeclaration, "value class is not supported by Lazyval.")
            valid = false
        }

        // Static and @Transient properties are excluded — only the storage payload counts.
        val publicProperties = classDeclaration.getAllProperties()
            .filter { !it.isStatic() && !it.isTransient() }
            .toList()
        val propertyAccessorPairs = findPropertyAccessorPairs(classDeclaration)

        if (propertyAccessorPairs.size > 1 || publicProperties.size > 1) {
            environment.error(classDeclaration,
                "Not a simple ValueType. Lazyval only supports classes with one non-transient property.")
            valid = false
        } else if (propertyAccessorPairs.isEmpty() && publicProperties.isEmpty()) {
            environment.error(classDeclaration,
                "No accessible properties found. Lazyval requires the ValueType to have exactly one accessible property.")
            return null
        }

        if (publicProperties.first().type.resolve().isMarkedNullable) {
            environment.error(publicProperties.first(),
                "Wrapped type must not be nullable. Please use a non-nullable type.")
            valid = false
        }

        val valueProperty = publicProperties.firstOrNull() ?: propertyAccessorPairs.first().first
        val accessorMethod = propertyAccessorPairs.firstOrNull()?.second

        val factoryMethods = findFactoryMethods(classDeclaration, valueProperty.type.resolve())
        if (factoryMethods.size > 1) {
            val functionNames = factoryMethods.joinToString(", ") { it.simpleName.asString() }
            environment.error(classDeclaration,
                "Multiple matching factory methods with the same signature found. Please check functions $functionNames")
            valid = false
        }
        val factoryMethod = factoryMethods.firstOrNull()

        if (!valid) {
            return null
        }
        if (Modifier.OPEN in classDeclaration.modifiers) {
            environment.warn(classDeclaration, NOT_FINAL_CLASS_WARNING)
        }
        if (valueProperty.isMutable) {
            environment.warn(valueProperty, NOT_FINAL_VALUE_WARNING)
        }
        return ValidatedKspGeneratorElement(
            classDeclaration,
            WrappedProperty(valueProperty),
            factoryMethod,
            accessorMethod)
    }

    private fun findPropertyAccessorPairs(
        classDeclaration: KSClassDeclaration
    ): List<Pair<KSPropertyDeclaration, KSFunctionDeclaration?>> {
        val properties = classDeclaration.getAllProperties()
            .filter { property ->
                !property.isStatic() &&
                        !property.isTransient() &&
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
            val accessor = methods.firstOrNull { method ->
                method.returnType?.resolve() == property.type.resolve() &&
                        (method.simpleName.asString() == property.simpleName.asString() ||
                                method.simpleName.asString() == "${property.simpleName.asString()}()")
            }

            // For data classes, the property itself is the accessor; for regular classes an explicit
            // accessor method is required.
            if (accessor != null || (classDeclaration.modifiers.contains(Modifier.DATA) && !property.isPrivate())) {
                Pair(property, accessor)
            } else {
                null
            }
        }
    }

    private fun findFactoryMethods(
        classDeclaration: KSClassDeclaration,
        wrappedType: KSType
    ): List<KSFunctionDeclaration> {
        val companionObject = classDeclaration.declarations
            .filterIsInstance<KSClassDeclaration>()
            .firstOrNull { it.isCompanionObject }
            ?: return emptyList()

        return companionObject.declarations
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { function ->
                if (function.parameters.size != 1) return@filter false

                val returnType = function.returnType?.resolve() ?: return@filter false
                val returnTypeMatches = returnType == classDeclaration.asStarProjectedType() ||
                        (returnType.isMarkedNullable && returnType.makeNotNullable() == classDeclaration.asStarProjectedType())
                if (!returnTypeMatches) return@filter false

                val paramType = function.parameters[0].type.resolve()
                paramType == wrappedType ||
                        (paramType.isMarkedNullable && paramType.makeNotNullable() == wrappedType)
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

    private fun KSPropertyDeclaration.isTransient(): Boolean {
        return annotations.any { annotation ->
            if (annotation.shortName.asString() != "Transient") return@any false
            val fqn = annotation.annotationType.resolve().declaration.qualifiedName?.asString()
            fqn == "kotlin.jvm.Transient" || fqn == "java.beans.Transient"
        }
    }
}
