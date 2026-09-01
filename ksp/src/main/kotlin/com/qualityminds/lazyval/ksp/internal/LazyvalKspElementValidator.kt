package com.qualityminds.lazyval.ksp.internal

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.*
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.qualityminds.lazyval.ksp.spi.WrappedProperty

private const val NOT_FINAL_CLASS_WARNING =
    "Value Types should not be extendable, hence the class should be final."
private const val NOT_FINAL_VALUE_WARNING =
    "Value Types should be immutable, hence the wrapped property should be final (val)."
private val TRANSIENT_ANNOTATIONS = setOf(
    "kotlin.jvm.Transient",
    "jakarta.persistence.Transient",
    "org.springframework.data.annotation.Transient")

/**
 * Validates a [KSClassDeclaration] against Lazyval's value-type contract and returns a
 * [ValidatedKspGeneratorElement] ready for code generation. Errors and warnings are reported
 * via the supplied [LazyvalKspEnvironment].
 *
 * Only the rules live in the class; finding and matching the symbols they judge sits as file-private
 * helpers below, following the same split [AccessorLookup] uses. The wording of every diagnostic is
 * one step further out, in `LazyvalKspElementValidatorMessages.kt`, because it answers to KspIT and to the APT validator
 * rather than to the rules. What that buys: whatever is in the class reports to the compiler and
 * therefore needs a test that reads the message back, and whatever is below it does not.
 */
internal class LazyvalKspElementValidator(private val environment: LazyvalKspEnvironment) {

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
        val payloadCandidates = pairs.filter { it.isReadable }
        if (payloadCandidates.isEmpty()) {
            // Two situations that look alike from the outside but call for different advice: a class
            // with nothing to wrap, versus one whose properties exist but sit behind a visibility the
            // generated code cannot pass. Reporting the latter on the class would tell the author that
            // nothing was found while they are looking straight at the property.
            if (pairs.isEmpty()) {
                environment.error(classDeclaration,
                    "No accessible properties found. Lazyval requires the ValueType to have exactly one accessible property.")
            } else {
                pairs.forEach { environment.error(it.property, it.unreachableReason) }
            }
            return null
        }
        val payloadValid = validatePayload(classDeclaration, payloadCandidates)
        val (valueProperty, accessorMethod) = payloadCandidates.first()

        val payloadType = valueProperty.type.resolve()
        val factoryMethods = findFactoryMethods(classDeclaration, payloadType)
        val factoryValid = validateFactoryMethods(classDeclaration, factoryMethods)
        // Only answerable once the payload is: with several candidates there is no single type to match
        // a constructor against, and the first property is a guess that would misname the fix.
        val reconstructionValid = !payloadValid ||
                validateReconstruction(classDeclaration, payloadType, factoryMethods)

        // Listed rather than and-ed so that adding a rule is one line and reads as one more entry in
        // the contract, instead of lengthening a condition nobody can take in at a glance.
        val everyRulePassed = listOf(shapeValid, payloadValid, factoryValid, reconstructionValid).all { it }
        if (!everyRulePassed) {
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
     * Reading the payload is only half the contract — the value also has to be reconstructible from
     * it. Generated code calls either a factory function or a constructor, both from another package,
     * so one it cannot reach is no better than one that does not exist. A factory settles the question
     * on its own; only in its absence does the constructor have to carry the weight.
     *
     * Left unchecked, either mistake surfaces as a kotlinc error inside generated sources, which is
     * exactly what Lazyval promises never to emit.
     */
    private fun validateReconstruction(
        classDeclaration: KSClassDeclaration,
        payloadType: KSType,
        factoryMethods: List<KSFunctionDeclaration>
    ): Boolean {
        if (factoryMethods.size > 1) {
            // Ambiguity is already reported by validateFactoryMethods, and until it is resolved there is
            // no single factory whose reachability could be judged.
            return true
        }
        factoryMethods.singleOrNull()?.let { factory ->
            if (factory.isPublic()) {
                return true
            }
            environment.error(factory, nonPublicFactoryMessage(factory))
            return false
        }
        val constructor = findPayloadConstructor(classDeclaration, payloadType)
        if (constructor == null) {
            environment.error(classDeclaration, missingConstructorMessage(classDeclaration, payloadType))
            return false
        }
        // `private` and `protected` are out of reach from another package whatever the module layout.
        // `internal` is deliberately left alone: an internal *class* is supported because class names
        // are not mangled the way an internal property's getter is — see PropertyAccessorPair.
        val visibility = constructor.getVisibility()
        if (visibility == Visibility.PRIVATE || visibility == Visibility.PROTECTED) {
            environment.error(constructor, nonPublicConstructorMessage(classDeclaration, constructor, visibility))
            return false
        }
        return true
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
}

/**
 * A candidate payload property together with the accessor generated code should call, which is
 * `null` when the property is read directly. Mirrors the APT validator's `FieldAccessorPair`.
 */
private data class PropertyAccessorPair(
    val property: KSPropertyDeclaration,
    val accessor: KSFunctionDeclaration?) {

    /**
     * Whether generated code can actually read this property, which is what makes the "exactly one
     * accessible property" contract true rather than aspirational.
     *
     * A pair that found an accessor is always readable — [AccessorLookup.accessorCandidates] only
     * ever offers public functions. Without one the property is read through the getter Kotlin
     * synthesizes, and that route exists only for public properties. Generated code is emitted into
     * its own package, so a property reachable neither way would yield source that does not compile.
     */
    val isReadable: Boolean
        get() = accessor != null || property.isPublic()

    /** Why [isReadable] is `false`, phrased as the change the author has to make. */
    val unreachableReason: String
        get() = unreachablePropertyMessage(property)
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
 *
 * Pairs generated code cannot read are returned as well rather than filtered out here, because the
 * caller needs them to tell "this class has nothing to wrap" apart from "this class hides what it
 * wraps" — and the second case is worth naming the offending property for. Select the ones eligible
 * as payload with [PropertyAccessorPair.isReadable].
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

            function.parameters[0].type.resolve().reconstructs(wrappedType)
        }
        .toList()
}

private fun findPayloadConstructor(
    classDeclaration: KSClassDeclaration,
    payloadType: KSType
): KSFunctionDeclaration? {
    return classDeclaration.getConstructors().firstOrNull { constructor ->
        constructor.parameters.size == 1 &&
                constructor.parameters[0].type.resolve().reconstructs(payloadType)
    }
}

/**
 * Whether a parameter of this type can carry the [payload] back into the value type. Nullability is
 * ignored on purpose and in one place, so that a factory and a constructor cannot disagree about it:
 * a parameter of `String?` still reconstructs a `String` payload.
 */
private fun KSType.reconstructs(payload: KSType): Boolean =
    this == payload || (isMarkedNullable && makeNotNullable() == payload)

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
