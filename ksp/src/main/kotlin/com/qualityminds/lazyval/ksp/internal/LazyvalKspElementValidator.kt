package com.qualityminds.lazyval.ksp.internal

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.*
import com.qualityminds.lazyval.ksp.spi.JavaAccessShim
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.qualityminds.lazyval.ksp.spi.DeclaredPayload
import com.qualityminds.lazyval.naming.DotName

private const val NOT_FINAL_CLASS_WARNING =
    "Value Types should not be extendable, hence the class should be final."
private const val NOT_FINAL_VALUE_WARNING =
    "Value Types should be immutable, hence the payload property should be final (val)."
private const val JVM_FIELD_ANNOTATION = "kotlin.jvm.JvmField"
private const val JVM_NAME_ANNOTATION = "kotlin.jvm.JvmName"
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
 *
 * The names generated Java has to call are a [JvmNameLookup] rather than a helper below, because they
 * are not a rule and not a judgement: they answer to the Kotlin compiler's naming, which no diagnostic
 * here has an opinion about. It is a collaborator rather than a member of [LazyvalKspEnvironment] so
 * that the environment keeps to reporting and configuration.
 */
internal class LazyvalKspElementValidator(
    private val environment: LazyvalKspEnvironment,
    private val jvmNames: JvmNameLookup) {

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
        val (valueProperty, accessorMethod) = payloadCandidates.first()
        val payloadType = valueProperty.type.resolve()
        // Resolved before the payload rules run because they judge it, and once because the element
        // needs the same answer to describe its Java-facing view.
        val valueClass = payloadType.inspectValueClass(environment.builtIns)
        val payloadValid = validatePayload(classDeclaration, payloadCandidates, valueClass)
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
        val factoryMethod = factoryMethods.firstOrNull()
        return ValidatedKspGeneratorElement(
            classDeclaration,
            DeclaredPayload(valueProperty),
            factoryMethod,
            accessorMethod,
            jvmNames.javaAccessorName(valueProperty, accessorMethod),
            jvmNames.javaFactoryPath(factoryMethod),
            (valueClass as? ValueClassInspection.Unwrappable)?.payload?.steps.orEmpty(),
            javaPayloadType(payloadType, valueClass),
            javaAccessShim(classDeclaration, valueProperty, valueClass))
    }

    /** Rules about the class itself, independent of what it wraps. */
    private fun validateShape(classDeclaration: KSClassDeclaration): Boolean {
        var valid = true
        // Generated code has to name the type before it can read or rebuild it, and it lands in another
        // package. `internal` clears that bar and leaks nothing by doing so: a caller outside the module
        // cannot name the type either, so the API generated around it keeps what the author kept.
        val visibility = classDeclaration.getVisibility()
        if (visibility != Visibility.PUBLIC && visibility != Visibility.INTERNAL) {
            environment.error(classDeclaration, nonPublicTypeMessage(classDeclaration))
            valid = false
        }
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
     * Rules about the payload: exactly one property, of a non-nullable type, readable through
     * an accessor, and — when it is a value class — one Lazyval can unwrap and re-wrap. All of them
     * describe the same thing, so a class that gets several wrong hears about each of them.
     */
    private fun validatePayload(
        classDeclaration: KSClassDeclaration,
        pairs: List<PropertyAccessorPair>,
        valueClass: ValueClassInspection
    ): Boolean {
        var valid = true
        if (pairs.size > 1) {
            environment.error(classDeclaration,
                "Not a simple ValueType. Lazyval only supports classes with one non-transient property.")
            valid = false
        }
        val (valueProperty, accessor) = pairs.first()
        val payloadType = valueProperty.type.resolve()
        if (payloadType.isMarkedNullable) {
            environment.error(valueProperty,
                "Payload must not be nullable. Please use a non-nullable type.")
            valid = false
        }
        // A value-class payload is supported by unwrapping it, so only the shapes that cannot be
        // unwrapped are refused. Reported on the payload because the type is the author's choice.
        if (valueClass is ValueClassInspection.Unsupported) {
            environment.error(valueProperty, valueClass.message)
            valid = false
        }
        // Generated Java reads the payload through a method and has no field path, so a property whose
        // getter was suppressed leaves it nothing to call. Only fatal without an accessor function:
        // with one, that function is the route and `@JvmField` is beside the point.
        if (accessor == null && valueProperty.annotations.hasMarker(JVM_FIELD_ANNOTATION)) {
            environment.error(valueProperty, jvmFieldPropertyMessage(valueProperty))
            valid = false
        }
        return valid
    }

    /**
     * At most one factory method may match the payload type; with several, Lazyval cannot tell which
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
            return validateFactoryVisibility(factory)
        }
        val constructor = findPayloadConstructor(classDeclaration, payloadType)
        if (constructor == null) {
            environment.error(classDeclaration, missingConstructorMessage(classDeclaration, payloadType))
            return false
        }
        // `private` and `protected` are out of reach from another package whatever the module layout.
        // `internal` is deliberately left alone: a constructor is `<init>` in the bytecode, so there is
        // no name for Kotlin to mangle a module suffix onto and generated Java can call it.
        val visibility = constructor.getVisibility()
        if (visibility == Visibility.PRIVATE || visibility == Visibility.PROTECTED) {
            environment.error(constructor, nonPublicConstructorMessage(classDeclaration, constructor, visibility))
            return false
        }
        return true
    }

    /**
     * Whether generated code may call [factory].
     *
     * `internal` is allowed, which puts a factory on the same footing as the `internal` constructor
     * accepted below: restricting construction to the declaring module is a coherent thing to ask for,
     * and unlike the payload property it does not oblige Lazyval to publish anything the author hid —
     * the value still reads through a public accessor either way.
     *
     * Two conditions attach to it, and both are about the name rather than the intent. It has to carry
     * `@JvmName`, because otherwise the JVM name ends in a module suffix that generated Java would have
     * to hard-code. And it has to be declared here: across a module boundary `internal` is genuinely
     * unreachable from the Kotlin half of the output, whatever the bytecode permits.
     */
    private fun validateFactoryVisibility(factory: KSFunctionDeclaration): Boolean {
        if (factory.isPublic()) {
            return true
        }
        if (factory.getVisibility() != Visibility.INTERNAL) {
            environment.error(factory, nonPublicFactoryMessage(factory))
            return false
        }
        if (!factory.isFromCurrentModule()) {
            environment.error(factory, internalFactoryFromOtherModuleMessage(factory))
            return false
        }
        if (!factory.annotations.hasMarker(JVM_NAME_ANNOTATION)) {
            environment.error(factory, internalFactoryWithoutJvmNameMessage(factory))
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
 * payload. Static and transient state is excluded because only the storage payload counts.
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

/**
 * The type generated Java carries in place of the payload. A value class is compiled away, so Java can
 * only ever see the type it wraps; for anything else the payload type stands as written.
 */
private fun javaPayloadType(payloadType: KSType, valueClass: ValueClassInspection): KSType =
    when (valueClass) {
        is ValueClassInspection.Unwrappable -> valueClass.payload.underlyingType
        else -> payloadType
    }

/**
 * The shim generated Java goes through, or `null` when it can read and rebuild the type directly.
 *
 * Named after the domain-primitive and placed in its package so it can reach an `internal` member, and
 * so two domain-primitives never collide — [DotName.flatName] already flattens nested names.
 */
private fun javaAccessShim(
    classDeclaration: KSClassDeclaration,
    valueProperty: KSPropertyDeclaration,
    valueClass: ValueClassInspection
): JavaAccessShim? {
    if (valueClass !is ValueClassInspection.Unwrappable) {
        return null
    }
    return JavaAccessShim(
        name = DotName.of(
            classDeclaration.packageName.asString(),
            "${classDeclaration.toDotName().flatName()}JvmAccess"),
        readMember = valueProperty.simpleName.asString(),
        createMember = "of")
}

private fun findFactoryMethods(
    classDeclaration: KSClassDeclaration,
    payloadType: KSType
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

            function.parameters[0].type.resolve().reconstructs(payloadType)
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

/**
 * Whether this declaration is compiled by the run that is processing it, rather than read from a jar.
 * Decides what `internal` means for it: a members-of-this-module question, or an unreachable one.
 *
 * Mirrors the discriminator `KspClassInspection.isFromCurrentModule` already uses.
 */
private fun KSDeclaration.isFromCurrentModule(): Boolean =
    origin == Origin.KOTLIN || origin == Origin.JAVA

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
 * Whether this annotation list carries [qualifiedName]. The short name is compared first so the common
 * case costs no type resolution, the way [hasTransientMarker] does it.
 *
 * `internal` rather than file-private because [JvmNameLookup] asks the same question of `@JvmStatic`,
 * and one spelling of the check beats two.
 */
internal fun Sequence<KSAnnotation>.hasMarker(qualifiedName: String): Boolean {
    val shortName = qualifiedName.substringAfterLast('.')
    return any { annotation ->
        if (annotation.shortName.asString() != shortName) return@any false
        annotation.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName
    }
}
