package com.qualityminds.lazyval.ksp.internal

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.symbol.*
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
        val TRANSIENT_ANNOTATIONS = setOf(
            "kotlin.jvm.Transient",
            "jakarta.persistence.Transient",
            "org.springframework.data.annotation.Transient")
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

    /**
     * Every rule is evaluated before the first failure is acted on, so an invalid class reports all
     * of its problems in one compiler run instead of one per fix-and-recompile cycle. The missing
     * payload is the sole exception: without it there is nothing to look a factory method up by.
     */
    private fun validateClass(classDeclaration: KSClassDeclaration): ValidatedKspGeneratorElement? {
        val shapeValid = validateShape(classDeclaration)

        val pairs = findPropertyAccessorPairs(classDeclaration)
        if (pairs.isEmpty()) {
            environment.error(classDeclaration,
                "No accessible properties found. Lazyval requires the ValueType to have exactly one accessible property.")
            return null
        }
        val payloadValid = validatePayload(classDeclaration, pairs)
        val (valueProperty, accessorMethod) = pairs.first()

        val factoryMethods = findFactoryMethods(classDeclaration, valueProperty.type.resolve())
        val factoryValid = validateFactoryMethods(classDeclaration, factoryMethods)

        if (!(shapeValid && payloadValid && factoryValid)) {
            return null
        }
        warnOnNonFinal(classDeclaration, valueProperty)
        return ValidatedKspGeneratorElement(
            classDeclaration,
            WrappedProperty(valueProperty),
            factoryMethods.firstOrNull(),
            accessorMethod)
    }

    /** Rules about the class itself, independent of what it wraps. */
    private fun validateShape(classDeclaration: KSClassDeclaration): Boolean {
        var valid = true
        if (Modifier.ABSTRACT in classDeclaration.modifiers) {
            environment.error(classDeclaration, "Abstract class is not a valid ValueType.")
            valid = false
        }
        if (Modifier.VALUE in classDeclaration.modifiers) {
            environment.error(classDeclaration, "value class is not supported by Lazyval.")
            valid = false
        }
        return valid
    }

    /**
     * Rules about the wrapped payload: exactly one property, of a non-nullable type. Both describe
     * the same thing, so a class that gets both wrong hears about both.
     */
    private fun validatePayload(
        classDeclaration: KSClassDeclaration,
        pairs: List<PropertyAccessorPair>
    ): Boolean {
        var valid = true
        if (pairs.size > 1) {
            environment.error(classDeclaration,
                "Not a simple ValueType. Lazyval only supports classes with one non-transient property.")
            valid = false
        }
        val valueProperty = pairs.first().property
        if (valueProperty.type.resolve().isMarkedNullable) {
            environment.error(valueProperty,
                "Wrapped type must not be nullable. Please use a non-nullable type.")
            valid = false
        }
        return valid
    }

    /**
     * At most one factory method may match the wrapped type; with several, Lazyval cannot tell which
     * one is meant to reconstruct the value. Having none is fine — the constructor is then used.
     */
    private fun validateFactoryMethods(
        classDeclaration: KSClassDeclaration,
        factoryMethods: List<KSFunctionDeclaration>
    ): Boolean {
        if (factoryMethods.size <= 1) {
            return true
        }
        val functionNames = factoryMethods.joinToString(", ") { it.simpleName.asString() }
        environment.error(classDeclaration,
            "Multiple matching factory methods with the same signature found. Please check functions $functionNames")
        return false
    }

    /**
     * Advice rather than a rule, and therefore only emitted once the type is known to be valid: a
     * class that is being rejected should not also be lectured about style.
     */
    private fun warnOnNonFinal(
        classDeclaration: KSClassDeclaration,
        valueProperty: KSPropertyDeclaration
    ) {
        if (Modifier.OPEN in classDeclaration.modifiers) {
            environment.warn(classDeclaration, NOT_FINAL_CLASS_WARNING)
        }
        if (valueProperty.isMutable) {
            environment.warn(valueProperty, NOT_FINAL_VALUE_WARNING)
        }
    }

    /**
     * Non-static, non-transient properties paired with their accessor — the candidates for being the
     * wrapped payload. Static and transient state is excluded because only the storage payload counts.
     *
     * Accessors are resolved before the transient filter runs, because a framework `@Transient` may
     * sit on the accessor instead of on the property.
     *
     * The accessor is deliberately allowed to be `null` so that properties of external Java types
     * (e.g. java.time.Year's `year`) still get a chance to be paired with a JavaBean accessor. KSP
     * reports `getter=null`/`hasBackingField=false` for those synthesized properties even though a
     * matching bean getter exists as a separate function.
     */
    private fun findPropertyAccessorPairs(
        classDeclaration: KSClassDeclaration
    ): List<PropertyAccessorPair> {
        val allMethods = classDeclaration.getAllFunctions().toList()
        // Tier 3 (type-only match) only runs for external Java declarations. Applying it to Kotlin
        // would wrongly pair a data class's property with its synthesized `component1()` accessor
        // and break the JavaBean output Mapstruct/JPA/etc. expect.
        val isExternalJavaType = classDeclaration.origin == Origin.JAVA ||
                classDeclaration.origin == Origin.JAVA_LIB

        return classDeclaration.getAllProperties()
            .filter { !it.isStatic() }
            .map { property ->
                PropertyAccessorPair(property, findAccessor(property, allMethods, isExternalJavaType))
            }
            .filter { !it.property.isTransient(it.accessor) }
            .toList()
    }

    private fun findFactoryMethods(
        classDeclaration: KSClassDeclaration,
        wrappedType: KSType
    ): List<KSFunctionDeclaration> {
        // Kotlin classes expose factories via companion objects. Java classes (e.g. java.time.Year.of)
        // expose them as JAVA_STATIC methods declared on the class itself. Scan both so external Java
        // types can be used as @LazyvalConfiguration.externalTypes — mirrors the APT validator, which
        // simply scans static methods on the TypeElement.
        val companionFactories = classDeclaration.declarations
            .filterIsInstance<KSClassDeclaration>()
            .firstOrNull { it.isCompanionObject }
            ?.declarations
            ?.filterIsInstance<KSFunctionDeclaration>()
            ?.toList()
            .orEmpty()

        val staticFactories = classDeclaration.getDeclaredFunctions()
            .filter { Modifier.JAVA_STATIC in it.modifiers }
            .toList()

        return (companionFactories + staticFactories)
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

    /**
     * True when the property, its getter, or a separately declared bean accessor is marked transient.
     * Kotlin routes an annotation written `@get:Transient` — and any Java annotation whose only
     * applicable target is `METHOD` — onto the getter, while for external Java types the accessor is
     * a standalone function, so all three sites have to be consulted.
     */
    private fun KSPropertyDeclaration.isTransient(accessor: KSFunctionDeclaration?): Boolean {
        return annotations.hasTransientMarker() ||
                getter?.annotations?.hasTransientMarker() == true ||
                accessor?.annotations?.hasTransientMarker() == true
    }

    private fun Sequence<KSAnnotation>.hasTransientMarker(): Boolean {
        return any { annotation ->
            if (annotation.shortName.asString() != "Transient") return@any false
            annotation.annotationType.resolve().declaration.qualifiedName?.asString() in TRANSIENT_ANNOTATIONS
        }
    }

    /**
     * A candidate payload property together with the accessor generated code should call, which is
     * `null` when the property is read directly. Mirrors the APT validator's `FieldAccessorPair`.
     */
    private data class PropertyAccessorPair(
        val property: KSPropertyDeclaration,
        val accessor: KSFunctionDeclaration?)
}
