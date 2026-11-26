package com.qualityminds.lazyval.ksp.internal

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.qualityminds.lazyval.naming.Payload

private const val JVM_STATIC_ANNOTATION = "kotlin.jvm.JvmStatic"

/**
 * Answers what a member is *called* in the bytecode, and what it takes and returns there: the things about
 * it that Java output cannot read off the Kotlin declaration. The companion question to [AccessorLookup],
 * which answers *which* member exposes the payload; together they settle what a generated Java call looks
 * like.
 *
 * Deliberately not part of [AccessorLookup], whose contract is to be a pure, KSP-free heuristic that
 * unit tests can drive without constructing KSP symbols. Every answer here comes from [Resolver], so
 * moving it there would cost that file the property it is organised around.
 *
 * Kotlin output has no use for any of this: it reads declarations as written, and `@JvmName` moves the
 * JVM name only.
 */
internal class JvmNameLookup(private val resolver: Resolver) {

    /**
     * The bytecode name generated Java has to call to read the payload. Differs from the property name
     * whenever `@JvmName` renames the getter, and also under Kotlin's own convention of keeping an
     * `is`-prefixed property's name as its getter (`isActive`, not `getIsActive`).
     *
     * Falls back to the JavaBean spelling when the name cannot be resolved: [jvmName] returns `null`
     * only on a resolution failure, and that spelling is what Lazyval assumed before it started asking
     * — no worse than it used to be, rather than nothing at all.
     */
    fun javaAccessorName(property: KSPropertyDeclaration, accessor: KSFunctionDeclaration?): String {
        if (accessor != null) {
            return jvmName(accessor) ?: accessor.simpleName.asString()
        }
        property.getter?.let { getter -> jvmName(getter)?.let { return it } }
        return "get" + property.simpleName.asString().replaceFirstChar { it.uppercase() }
    }

    /**
     * Where generated Java finds the factory, relative to the domain-primitive's type.
     *
     * `@JvmStatic` (and a Java `static`) put the function on the type itself, so its JVM name is the
     * whole path. Without it Kotlin compiles a companion function onto the companion class alone,
     * reachable from Java through the companion's field — which is what an author writing a plain
     * `companion object { fun of(..) }` gets, instead of a call to a method that is not there.
     */
    fun javaFactoryPath(factory: KSFunctionDeclaration): String {
        val jvmName = jvmName(factory) ?: factory.simpleName.asString()
        if (factory.annotations.hasMarker(JVM_STATIC_ANNOTATION) ||
            Modifier.JAVA_STATIC in factory.modifiers) {
            return jvmName
        }
        val companion = factory.parentDeclaration as? KSClassDeclaration
        return if (companion?.isCompanionObject == true) {
            "${companion.simpleName.asString()}.$jvmName"
        } else {
            jvmName
        }
    }

    /**
     * The payload as generated Java carries it. Kotlin's type names the payload, but cannot tell whether a
     * Java declaration holds it as a primitive: Java's `int` and `Integer` both reach Kotlin as `Int`, an
     * unannotated `Integer` as `Int!`, and one under JSpecify's `@NonNull` as plain `Int`. So a payload Kotlin
     * sees as a primitive is checked against the JVM signature of what Java reads it through, and is the
     * boxed type where that returns a reference.
     *
     * An [unwrapped] payload keeps Kotlin's answer: generated Java reaches a value class through the shim,
     * which carries the unwrapped type as Kotlin declares it.
     */
    fun javaPayload(
        payloadType: KSType,
        property: KSPropertyDeclaration,
        accessor: KSFunctionDeclaration?,
        unwrapped: Boolean
    ): Payload {
        val payload = payloadType.toPayload()
        if (payload !is Payload.Primitive || unwrapped) {
            return payload
        }
        val readsReference = jvmSignature(accessor ?: property)?.substringAfter(')')?.isReferenceType() ?: false
        return if (readsReference) Payload.Declared(payload.kind.boxed()) else payload
    }

    /**
     * The primitive a boxed payload has to be unboxed to before generated Java passes it to [creator], or
     * `null` where [creator] takes a reference on the JVM, as a Kotlin `Int?` or a Java `Integer` does. Passed
     * unboxed to a creator that takes a reference, or boxed to one that takes a primitive, the value binds to
     * whatever overload javac prefers for that form instead of to [creator].
     *
     * Read off the JVM signature for the reason given at [javaPayload], and falls back to Kotlin's nullability
     * where the signature cannot be resolved. [creator] is `null` where Java goes through the shim, which takes
     * the unwrapped payload as Kotlin declares it: non-null, by the value-class rules.
     */
    fun javaCreatePrimitive(payload: Payload, creator: KSFunctionDeclaration?): Payload.Kind? {
        if (payload !is Payload.Primitive) {
            return null
        }
        val takesReference = creator?.let { function ->
            jvmSignature(function)?.substringAfter('(')?.isReferenceType()
                ?: function.parameters.single().type.resolve().isMarkedNullable
        } ?: false
        return if (takesReference) null else payload.kind
    }

    /**
     * The JVM signature of [declaration]: a method descriptor such as `(Ljava/lang/Integer;)V` for a function,
     * a type descriptor such as `I` for a property. `null` signals a resolution failure.
     */
    @OptIn(KspExperimental::class)
    private fun jvmSignature(declaration: KSDeclaration): String? = resolver.mapToJvmSignature(declaration)

    /**
     * The name [function] carries in the bytecode. Differs from the Kotlin name whenever `@JvmName`
     * renames it, and carries a `$module` suffix when the function is `internal` — which is why an
     * internal factory is asked for `@JvmName` rather than having that suffix hard-coded into
     * generated Java.
     *
     * `null` signals a resolution failure rather than "no JVM name"; callers fall back to the Kotlin
     * name.
     */
    @OptIn(KspExperimental::class)
    private fun jvmName(function: KSFunctionDeclaration): String? = resolver.getJvmName(function)

    /**
     * The name [accessor] carries in the bytecode. Mirrors [jvmName] for a property's getter, where
     * `@get:JvmName` plays the same role `@JvmName` does on a function.
     */
    @OptIn(KspExperimental::class)
    private fun jvmName(accessor: KSPropertyAccessor): String? = resolver.getJvmName(accessor)
}

/** Whether the JVM type descriptor this string starts with names a reference type or an array, not a primitive. */
private fun String.isReferenceType(): Boolean = startsWith('L') || startsWith('[')
