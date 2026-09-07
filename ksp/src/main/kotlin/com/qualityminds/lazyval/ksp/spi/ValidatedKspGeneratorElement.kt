package com.qualityminds.lazyval.ksp.spi

import com.google.devtools.ksp.symbol.*
import com.qualityminds.lazyval.ksp.internal.AccessPlan
import com.qualityminds.lazyval.ksp.internal.UnwrapStep
import com.qualityminds.lazyval.ksp.internal.toDotName
import com.qualityminds.lazyval.ksp.internal.toPayload
import com.qualityminds.lazyval.naming.DotName
import com.qualityminds.lazyval.naming.Payload
import org.jetbrains.annotations.ApiStatus


/**
 * A domain-primitive that passed validation, together with everything a generator needs to read its
 * payload and rebuild it.
 *
 * Expressions come in a Kotlin and a Java flavour, [kotlin] and [java], because the two languages see
 * different names: Kotlin output reads the declaration as written, while Java output has to use the
 * name the member actually carries in the bytecode, which `@JvmName` and `internal` both move. Those
 * JVM names are resolved once during validation, so no generator has to guess them from the Kotlin
 * declaration — guessing is what used to make `@JvmName` emit Java that could not compile.
 *
 * A payload that is a Kotlin `value class` is a further special case, because Kotlin compiles the
 * wrapper away and leaves nothing Java can name. Such a payload is *unwrapped*: [payloadType] is the
 * type it wrapped, and [javaAccessShim] names a generated Kotlin object that Java goes through. Both
 * facades already account for it, so a generator never has to know a value class was involved.
 *
 * So a generator asks for a name and for whole expressions, and assembles neither itself:
 *
 * ```
 * val codecName = element.name.flatName() + "Codec"    // "IdsProductIdCodec"
 * val carried = element.payloadType                    // what the codec reads and writes
 *
 * element.kotlin.read("instance")                      // Kotlin output
 * element.java.create("value")                         // Java output
 * ```
 *
 * The rules a `value class` payload has to satisfy, and what Lazyval rejects outright, are documented
 * under [value class payloads](https://qualityminds.github.io/lazyval/lazyval/main/rules.html#value-class);
 * a runnable generator built on this API lives in
 * [examples/kotlin-spi-dev](https://github.com/QualityMinds/lazyval/tree/main/examples/kotlin-spi-dev).
 *
 * Lazyval builds these; the constructor is `internal`. Nothing is lost by closing it — a generator
 * cannot conjure a [KSClassDeclaration] outside a running compilation — and it keeps this record free
 * to carry Lazyval's own types rather than widening the SPI to describe them.
 *
 * @param javaAccessorName bytecode name of the getter or accessor function Java output has to call
 * @param javaFactoryPath the dot-path from the Java type down to the factory method — `"of"` for a
 *                        `@JvmStatic` function, `"Companion.of"` (or the companion's own name) for one
 *                        without, since Kotlin then compiles it onto the companion class rather than
 *                        onto the type. `null` when there is no factory and Java output has to call
 *                        the constructor. Kotlin output has no use for it: in Kotlin a companion
 *                        function is reachable through the type either way.
 */
@ApiStatus.Experimental()
@ConsistentCopyVisibility
data class ValidatedKspGeneratorElement internal constructor(
    val element: KSClassDeclaration,
    /** The payload property as declared. See [DeclaredPayload] — most generators want [payload]. */
    val declaredPayload: DeclaredPayload,
    val factoryMethod: KSFunctionDeclaration?,
    private val accessorMethod: KSFunctionDeclaration?,
    private val javaAccessorName: String,
    private val javaFactoryPath: String?,
    /**
     * The `value class` chain from the declared payload down to [payloadType], outermost first, and
     * empty when the payload is carried as declared.
     */
    private val unwrapping: List<UnwrapStep>,
    /**
     * The type generated code carries in place of the payload, in either language. Identical to
     * [DeclaredPayload.type] unless the payload is a `value class`, in which case it is the type at the
     * end of the wrapping chain — the one thing that exists at runtime, since the JVM erases the
     * wrappers away. Every generator should map to this rather than to the declared payload type: a
     * value class is not a type any framework can persist, serialize or validate.
     */
    val payloadType: KSType,
    /**
     * The generated Kotlin object generated Java must go through to reach the payload, or `null` when
     * the domain-primitive can be read and rebuilt directly.
     *
     * Present only for a `value class` payload, whose accessor carries a signature hash and whose
     * enclosing constructor is private in the bytecode. [java] already routes through it, so consulting
     * this is only needed to emit something other than a read or a rebuild.
     */
    val javaAccessShim: JavaAccessShim?
){
    /**
     * This domain-primitive's own name, in the four spellings generated code needs — most often
     * [DotName.flatName], to derive the name of a generated class from it.
     *
     * ```
     * val codecName = element.name.flatName() + "Codec"   // "IdsProductIdCodec"
     * ```
     */
    val name: DotName = element.toDotName()

    // Every expression is spelled in AccessPlan, over plain strings and names. Keeping the assembly in
    // one place that needs no compiler is what lets AccessPlanSpec assert the expressions directly;
    // this class contributes the answers only KSP can give.
    private val plan: AccessPlan by lazy {
        AccessPlan(
            kotlinAccessor = rawKotlinAccessor,
            kotlinFactory = factoryMethod?.simpleName?.asString(),
            name = name,
            javaAccessorName = javaAccessorName,
            javaFactoryPath = javaFactoryPath,
            unwrapping = unwrapping,
            shim = javaAccessShim)
    }

    /** Expressions for generated Kotlin. See [KotlinPayload] for why they are worth asking for. */
    val kotlin: KotlinPayload by lazy { KotlinPayload(plan) }

    /** Expressions for generated Java. See [JavaPayload] for the three names Java cannot guess. */
    val java: JavaPayload by lazy { JavaPayload(plan) }

    /**
     * [payloadType]'s name as the JVM sees it, for the two things a code writer cannot give you: an
     * identifier to build a generated name from, and the reference name a primitive becomes where only
     * an object will do.
     *
     * ```
     * "map" + element.payload.identifier() + "To" + element.name.flatName()
     * ```
     *
     * Reads the *unwrapped* type, so a `value class` over `Int` names `int` — which is what the JVM
     * carries. Kotlin output that needs a *type* rather than a name should map [payloadType] with
     * KotlinPoet instead; this exists for the cases KotlinPoet and JavaPoet cannot answer.
     */
    val payload: Payload by lazy { payloadType.toPayload() }

    /**
     * Whether [payloadType] is a primitive on the JVM, and so whether generated code needs a null check
     * at all. Reads the *unwrapped* type, so a `value class` over `Int` counts as primitive — which is
     * what it is at runtime, and where the payload *as declared* would say no.
     */
    val isPayloadPrimitive: Boolean get() = payload is Payload.Primitive

    private val rawKotlinAccessor: String by lazy {
        when {
        // If there's an explicit accessor method, use it (make sure to call as a function with '()')
        accessorMethod != null -> "${accessorMethod.simpleName.asString()}()"
        // For data classes, properties are automatically accessible
        element.modifiers.contains(Modifier.DATA) -> declaredPayload.name
        // For regular classes, assume properties are accessible (make sure to call as a property without '()')
        else -> declaredPayload.name}
    }
}

/**
 * A generated Kotlin object that gives Java a way in when the domain-primitive itself offers none.
 *
 * Kotlin compiles a `value class` away: the payload accessor's JVM name carries a signature hash and
 * the enclosing constructor turns private, neither of which Java can spell. The shim is ordinary Kotlin
 * on both counts, and `@JvmStatic` puts its members on the class where Java expects them.
 *
 * Declared `internal`, so it is callable from generated Java in the same compilation while remaining
 * invisible to Kotlin callers outside the module — it adds no public API.
 *
 * @param name name of the generated object; the domain-primitive's own package, and a flattened
 *             version of its name, so two domain-primitives never collide
 * @param readMember member reading the unwrapped payload out of an instance
 * @param createMember member rebuilding the domain-primitive from an unwrapped payload
 */
@ApiStatus.Experimental()
data class JavaAccessShim(
    val name: DotName,
    val readMember: String,
    val createMember: String
)

/**
 * The payload property *as declared* — which for a `value class` payload is the wrapper, not the type
 * generated code ends up carrying.
 *
 * Almost every generator wants [ValidatedKspGeneratorElement.payload] — or
 * [ValidatedKspGeneratorElement.payloadType], for a code writer — instead: those describe what survives
 * to runtime, and so what a framework can persist, serialize or validate. Reach for this one only to
 * say something about the declaration itself — naming the value class in a doc comment, or in a builder
 * that hands back the declared type.
 *
 * There is deliberately no `isPrimitive()` here. A `value class` over `Int` is not a primitive as
 * declared but is one at runtime, and the only thing that answer ever decides is whether generated code
 * needs a null check — for which [ValidatedKspGeneratorElement.isPayloadPrimitive] is the one correct
 * source.
 */
@Suppress("unused") // property: escape hatch to the raw declaration, unused inside Lazyval
data class DeclaredPayload(val property: KSPropertyDeclaration) {
    val type: KSType = property.type.resolve()
    val name: String = property.simpleName.asString()
}
