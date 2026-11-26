package com.qualityminds.lazyval.ksp.internal

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.*
import com.qualityminds.lazyval.ksp.spi.DeclaredPayload
import com.qualityminds.lazyval.ksp.spi.JavaAccessShim
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.qualityminds.lazyval.naming.DotName

private const val JVM_FIELD_ANNOTATION = "kotlin.jvm.JvmField"
private const val JVM_NAME_ANNOTATION = "kotlin.jvm.JvmName"
private val REACHABLE_TYPE = setOf(Visibility.PUBLIC, Visibility.INTERNAL)
/** `Array<T>` and the primitive arrays — which is also how KSP presents a Java array. */
private val ARRAY_TYPES = setOf(
    "kotlin.Array", "kotlin.BooleanArray", "kotlin.ByteArray", "kotlin.CharArray", "kotlin.ShortArray",
    "kotlin.IntArray", "kotlin.LongArray", "kotlin.FloatArray", "kotlin.DoubleArray")

/**
 * Judges a [KSClassDeclaration] by Lazyval's rules, reports every rule it breaks via the supplied
 * [LazyvalKspEnvironment], and describes a class that breaks none as a [ValidatedKspGeneratorElement] for the
 * generators.
 *
 * [validate] is the list of rules, in the order they are judged, and each block in it starts with the section of
 * `rules.adoc` that explains the rule to users. What a rule judges is looked up beforehand, by convention, and
 * handed to it as data: [PayloadLookup] says which property holds the payload, [CreatorLookup] what rebuilds the
 * value from it, [inspectValueClass] how a value class is unwrapped. The wording of every diagnostic is in
 * `LazyvalKspElementValidatorMessages.kt`. The APT validator is laid out the same way.
 *
 * The names generated Java has to call are a [JvmNameLookup] rather than a helper, because they are not a rule
 * and not a judgement: they answer to the Kotlin compiler's naming, which no rule has an opinion about. It is a
 * collaborator rather than a member of [LazyvalKspEnvironment] so that the environment keeps to reporting and
 * configuration.
 */
internal class LazyvalKspElementValidator(
    private val environment: LazyvalKspEnvironment,
    private val jvmNames: JvmNameLookup) {

    /**
     * Every rule is judged before the class is rejected, so a class that breaks several hears about each of them
     * in one compiler run instead of in one fix-and-recompile cycle apiece. Only a missing subject ends the
     * judgement early: a declaration that is not a class, or one without exactly one payload, leaves the rules
     * after it nothing to judge.
     */
    @Suppress("CyclomaticComplexMethod") // the list of rules itself: splitting it up would scatter them again
    fun validate(type: KSClassDeclaration): ValidatedKspGeneratorElement? {
        // Shape: a class or a data class. An object, an interface, an enum or an annotation class has no instance
        // to rebuild, and nothing below would make sense of it.
        if (type.classKind != ClassKind.CLASS) {
            return reject(listOf(type to UNSUPPORTED_KIND_MESSAGE))
        }
        val errors = mutableListOf<Pair<KSNode, String>>()
        // Visibility: generated code lands in another package and has to name the type there. `internal` clears
        // that bar and leaks nothing, since a caller outside the module cannot name the type either.
        if (type.getVisibility() !in REACHABLE_TYPE) errors += type to nonPublicTypeMessage(type)
        // Shape: an abstract class has nothing to instantiate, and a value class is unsupported by design.
        if (Modifier.ABSTRACT in type.modifiers) errors += type to ABSTRACT_CLASS_MESSAGE
        if (Modifier.VALUE in type.modifiers) errors += type to VALUE_CLASS_MESSAGE

        // Exactly one payload that generated code can read. Without it, nothing is left to judge.
        val payload = when (val lookup = PayloadLookup.of(type, environment.builtIns)) {
            PayloadLookup.None -> return reject(errors + (type to NO_PROPERTY_MESSAGE))
            // Visibility: each property nothing can read, reported on the property the author is looking at.
            is PayloadLookup.Unreadable ->
                return reject(errors + lookup.properties.map { it to unreachablePropertyMessage(it) })
            is PayloadLookup.Several -> return reject(errors + (type to SEVERAL_PROPERTIES_MESSAGE))
            is PayloadLookup.Found -> lookup
        }
        // Payload type: not nullable, not an array, and a value class only where Lazyval can unwrap it and build
        // it back. Value class payloads lists the shapes it cannot, each of them legal Kotlin.
        if (payload.type.isMarkedNullable) errors += payload.property to NULLABLE_PAYLOAD_MESSAGE
        if (payload.type.isArray) errors += payload.property to arrayPayloadMessage(payload.property)
        if (payload.valueClass is ValueClassInspection.Unsupported) {
            errors += payload.property to payload.valueClass.message
        }
        // Visibility: without an accessor function, generated Java reads through the getter, which @JvmField
        // suppresses.
        if (payload.accessor == null && payload.property.annotations.hasMarker(JVM_FIELD_ANNOTATION)) {
            errors += payload.property to jvmFieldPropertyMessage(payload.property)
        }

        // Factory methods: the value is rebuilt through the factory where there is exactly one, through the
        // constructor taking the payload otherwise — reachable from generated code either way.
        val creator = CreatorLookup.of(type, payload.type)
        when (creator) {
            is CreatorLookup.SeveralFactories ->
                errors += type to severalFactoriesMessage(creator.factories)
            is CreatorLookup.Factory ->
                unreachableFactoryMessage(creator.declaration)?.let { errors += creator.declaration to it }
            is CreatorLookup.Constructor ->
                unreachableConstructorMessage(type, creator.declaration)?.let { errors += creator.declaration to it }
            is CreatorLookup.NullableConstructor ->
                errors += creator.declaration to nullableConstructorMessage(type, creator.declaration, payload.type)
            CreatorLookup.Missing ->
                errors += type to missingConstructorMessage(type, payload.type)
        }
        if (creator !is CreatorLookup.Found || errors.isNotEmpty()) {
            return reject(errors)
        }

        // Immutability: advice rather than rules, given only to a class every rule accepted.
        if (Modifier.OPEN in type.modifiers) environment.warn(type, NOT_FINAL_CLASS_WARNING)
        if (payload.property.isMutable) environment.warn(payload.property, NOT_FINAL_PAYLOAD_WARNING)
        return describe(type, payload, creator)
    }

    /** Reports each of [errors], and nothing is generated for the class. */
    private fun reject(errors: List<Pair<KSNode, String>>): ValidatedKspGeneratorElement? {
        errors.forEach { (node, message) -> environment.error(node, message) }
        return null
    }

    /**
     * Everything about an accepted class that generated code depends on, worked out once and here — the one place
     * that knows both what Lazyval matched and how the JVM sees it. Generated Java in particular calls the factory
     * or constructor [CreatorLookup] found, so the unboxing decided for it can never be decided for some other
     * overload.
     */
    private fun describe(
        type: KSClassDeclaration,
        payload: PayloadLookup.Found,
        creator: CreatorLookup.Found
    ): ValidatedKspGeneratorElement {
        val unwrappable = payload.valueClass as? ValueClassInspection.Unwrappable
        // A value class is compiled away, so generated code can only ever carry the type it wraps.
        val carriedType = unwrappable?.payload?.underlyingType ?: payload.type
        val shim = unwrappable?.let { javaAccessShim(type, payload.property) }
        val carried = jvmNames.javaPayload(carriedType, payload.property, payload.accessor, unwrapped = shim != null)
        val plan = AccessPlan(
            name = type.toDotName(),
            kotlinAccessor = payload.accessor?.let { "${it.simpleName.asString()}()" }
                ?: payload.property.simpleName.asString(),
            creator = when (creator) {
                is CreatorLookup.Factory -> Creator.Factory(
                    kotlinName = creator.declaration.simpleName.asString(),
                    javaPath = jvmNames.javaFactoryPath(creator.declaration),
                    returnsNullable = creator.declaration.returnType?.resolve()?.isMarkedNullable == true)
                is CreatorLookup.Constructor -> Creator.Constructor
            },
            javaAccessorName = jvmNames.javaAccessorName(payload.property, payload.accessor),
            unwrapping = unwrappable?.payload?.steps.orEmpty(),
            shim = shim,
            // The shim takes over rebuilding for generated Java, so nothing of the class's own is called then.
            javaPrimitive = jvmNames.javaCreatePrimitive(carried, if (shim == null) creator.declaration else null))
        val factory = (creator as? CreatorLookup.Factory)?.declaration
        return ValidatedKspGeneratorElement(type, DeclaredPayload(payload.property), factory, carriedType, carried, plan)
    }
}

/**
 * Why generated code may not call [factory], or `null` where it may.
 *
 * `internal` is allowed, which puts a factory on the same footing as the `internal` constructor
 * [unreachableConstructorMessage] accepts: restricting construction to the declaring module is a coherent thing
 * to ask for, and unlike the payload property it does not oblige Lazyval to publish anything the author hid — the
 * value still reads through a public accessor either way.
 *
 * Two conditions attach to it, and both are about the name rather than the intent. It has to carry `@JvmName`,
 * because otherwise the JVM name ends in a module suffix that generated Java would have to hard-code. And it has
 * to be declared here: across a module boundary `internal` is genuinely unreachable from the Kotlin half of the
 * output, whatever the bytecode permits.
 */
private fun unreachableFactoryMessage(factory: KSFunctionDeclaration): String? = when {
    factory.isPublic() -> null
    factory.getVisibility() != Visibility.INTERNAL -> nonPublicFactoryMessage(factory)
    !factory.isFromCurrentModule() -> internalFactoryFromOtherModuleMessage(factory)
    !factory.annotations.hasMarker(JVM_NAME_ANNOTATION) -> internalFactoryWithoutJvmNameMessage(factory)
    else -> null
}

/**
 * Why generated code may not call [constructor], or `null` where it may. `private` and `protected` are out of
 * reach from another package whatever the module layout. `internal` is deliberately left alone: a constructor is
 * `<init>` in the bytecode, so there is no name for Kotlin to mangle a module suffix onto, and generated Java can
 * call it.
 */
private fun unreachableConstructorMessage(type: KSClassDeclaration, constructor: KSFunctionDeclaration): String? =
    when (val visibility = constructor.getVisibility()) {
        Visibility.PRIVATE, Visibility.PROTECTED -> nonPublicConstructorMessage(type, constructor, visibility)
        else -> null
    }

/**
 * Whether this declaration is compiled by the run that is processing it, rather than read from a jar. Decides what
 * `internal` means for it: a members-of-this-module question, or an unreachable one.
 *
 * Mirrors the discriminator `KspClassInspection.isFromCurrentModule` already uses.
 */
private fun KSDeclaration.isFromCurrentModule(): Boolean =
    origin == Origin.KOTLIN || origin == Origin.JAVA

/**
 * The shim generated Java goes through for a `value class` payload, which Kotlin leaves it no accessor and no
 * constructor to call.
 *
 * Named after the domain-primitive and placed in its package so it can reach an `internal` member, and so two
 * domain-primitives never collide — [DotName.flatName] already flattens nested names.
 */
private fun javaAccessShim(type: KSClassDeclaration, property: KSPropertyDeclaration): JavaAccessShim =
    JavaAccessShim(
        name = DotName.of(type.packageName.asString(), "${type.toDotName().flatName()}JvmAccess"),
        readMember = property.simpleName.asString(),
        createMember = "of")

/**
 * Whether this type is an array, which no payload may be — the domain-primitive's own, nor the one a
 * value class wraps. A property to read like [KSType.isMarkedNullable], the rule it sits next to.
 *
 * Looks through a type alias, because KSP reports the alias rather than the type it stands for, and
 * `typealias Hash = ByteArray` is an array all the same.
 *
 * `internal` because [inspectValueClass] applies the rule to what a value class wraps, and one spelling of
 * the check beats two.
 */
internal val KSType.isArray: Boolean
    get() = when (val declaration = declaration) {
        is KSTypeAlias -> declaration.type.resolve().isArray
        else -> declaration.qualifiedName?.asString() in ARRAY_TYPES
    }

/**
 * Whether this annotation list carries [qualifiedName]. The short name is compared first so the common
 * case costs no type resolution.
 *
 * `internal` because [JvmNameLookup] asks the same question of `@JvmStatic`, and [inspectValueClass] of
 * `@JvmInline`; one spelling of the check beats several.
 */
internal fun Sequence<KSAnnotation>.hasMarker(qualifiedName: String): Boolean {
    val shortName = qualifiedName.substringAfterLast('.')
    return any { annotation ->
        if (annotation.shortName.asString() != shortName) return@any false
        annotation.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName
    }
}
