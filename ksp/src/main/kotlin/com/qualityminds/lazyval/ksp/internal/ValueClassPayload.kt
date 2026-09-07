package com.qualityminds.lazyval.ksp.internal

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSBuiltIns
import com.google.devtools.ksp.symbol.*

internal const val JVM_INLINE_ANNOTATION = "kotlin.jvm.JvmInline"

/**
 * Kotlin's unsigned types, which are value classes whose property and constructor are both `internal`
 * to the standard library — so the ordinary property/constructor route cannot touch them. They do
 * guarantee a conversion pair, which is the only way in and is stable API.
 *
 * A closed table rather than a search: looking for "some member returning the underlying type" would
 * find `hashCode()` on `UInt` just as readily as `toInt()`, and the re-wrapping half lives in a
 * top-level extension (`Int.toUInt()`) that cannot be found from the value class at all.
 */
private class UnsignedConversion(
    val unwrap: String,
    val wrap: String,
    val underlying: (KSBuiltIns) -> KSType)

private val UNSIGNED_CONVERSIONS = mapOf(
    "kotlin.UInt" to UnsignedConversion("toInt", "toUInt") { it.intType },
    "kotlin.ULong" to UnsignedConversion("toLong", "toULong") { it.longType },
    "kotlin.UShort" to UnsignedConversion("toShort", "toUShort") { it.shortType },
    "kotlin.UByte" to UnsignedConversion("toByte", "toUByte") { it.byteType })

/**
 * One level of a value-class chain: how to read the wrapped value out, and how to put it back.
 *
 * Two shapes rather than one, because the two families of value class are reached differently and a
 * single spelling would fit neither: an ordinary one through its property and its constructor or
 * factory, an unsigned one through a conversion pair. Nothing downstream cares which — [AccessPlan]
 * folds a list of these without asking.
 *
 * Pure strings, so that a chain can be built and asserted in a unit test.
 */
internal sealed interface UnwrapStep {

    /** Kotlin expression reading this level's value out of [receiver]. */
    fun read(receiver: String): String

    /** Kotlin expression putting [value] back into this level. */
    fun build(value: String): String

    /**
     * The ordinary route, which is how every value class outside the standard library is reached.
     *
     * @param name the single property holding the value
     * @param builder qualified callable that rebuilds it — the type's own name for a constructor, or
     *                the type plus a factory name, which is why one string covers both
     */
    data class Property(val name: String, val builder: String) : UnwrapStep {
        override fun read(receiver: String): String = "$receiver.$name"
        override fun build(value: String): String = "$builder($value)"
    }

    /**
     * The conversion route, for the standard library's unsigned types.
     *
     * @param toUnderlying member converting down, called on the value class (`UInt.toInt()`)
     * @param toWrapper extension converting back up, called on the underlying value (`Int.toUInt()`)
     */
    data class Conversion(val toUnderlying: String, val toWrapper: String) : UnwrapStep {
        override fun read(receiver: String): String = "$receiver.$toUnderlying()"
        override fun build(value: String): String = "$value.$toWrapper()"
    }
}

/**
 * How generated code gets from a `value class` payload to the value it can actually carry, and back.
 *
 * A value class is compiled away rather than merely renamed: the accessor's JVM name gains a signature
 * hash, its type erases to the underlying one, and the enclosing domain-primitive's constructor turns
 * private in the bytecode. None of that is expressible in Java, so Lazyval unwraps the payload and
 * reaches it through a generated Kotlin shim instead.
 *
 * Unwrapping obliges Lazyval to be able to *re-wrap*, which is why [steps] describe both directions,
 * and why inspecting a payload type can fail.
 */
internal data class ValueClassPayload(
    val declaration: KSClassDeclaration,
    /** The type the value class wraps — what generated code carries in its place. */
    val underlyingType: KSType,
    /**
     * The chain from the declared payload down to [underlyingType], outermost first. Never empty:
     * an ordinary payload gets no `ValueClassPayload` at all.
     */
    val steps: List<UnwrapStep>
)

/** The outcome of asking whether a payload type is an unwrappable value class. */
internal sealed interface ValueClassInspection {

    /** Not a value class; the payload needs no unwrapping and no shim. */
    data object NotAValueClass : ValueClassInspection

    data class Unwrappable(val payload: ValueClassPayload) : ValueClassInspection

    /** A value class Lazyval cannot unwrap; [message] is ready to report on the payload property. */
    data class Unsupported(val message: String) : ValueClassInspection
}

/**
 * Whether this type is a Kotlin `value class`.
 *
 * Two signals because one is not enough: `Modifier.VALUE` is reported for a declaration in the
 * compilation unit, but not for one read off the classpath — `kotlin.UInt` arrives with neither the
 * modifier nor anything else naming it a value class except `@JvmInline`, which is retained.
 */
internal fun KSType.isValueClass(): Boolean =
    Modifier.VALUE in declaration.modifiers ||
            declaration.annotations.hasMarker(JVM_INLINE_ANNOTATION)

/**
 * Works out how to unwrap and re-wrap a value-class payload, or why it cannot be done.
 *
 * Every rejection is a shape Kotlin permits: a value class may hide its property, hide its constructor
 * behind a factory, or wrap another value class. Each would otherwise surface as an error inside the
 * generated shim — a Kotlin error, since the shim is Kotlin, which makes it no less of a broken build.
 */
internal fun KSType.inspectValueClass(builtIns: KSBuiltIns): ValueClassInspection {
    if (!isValueClass()) {
        return ValueClassInspection.NotAValueClass
    }
    val outermost = this.declaration as? KSClassDeclaration
        ?: return ValueClassInspection.Unsupported(valueClassUnreadableMessage(this))

    // Transitively, because a value class may wrap another one — `Amount(UInt)` is two levels — and
    // the JVM flattens the lot to a single erased type anyway. Stopping at one level would refuse a
    // payload whose runtime representation the platform has already settled.
    var current: KSType = this
    val steps = mutableListOf<UnwrapStep>()
    val seen = mutableSetOf<String>()
    while (current.isValueClass()) {
        val fqn = current.declaration.qualifiedName?.asString()
            ?: return ValueClassInspection.Unsupported(valueClassUnreadableMessage(this))
        // A value class cannot really wrap itself, but a broken or half-resolved declaration could
        // look as though it does, and an unbounded loop is a worse failure than a diagnostic.
        if (!seen.add(fqn)) {
            return ValueClassInspection.Unsupported(valueClassUnreadableMessage(this))
        }
        when (val level = current.unwrapOneLevel(builtIns)) {
            is Level.Unsupported -> return ValueClassInspection.Unsupported(level.message)
            is Level.Next -> {
                steps += level.step
                current = level.next
            }
        }
    }
    return ValueClassInspection.Unwrappable(ValueClassPayload(outermost, current, steps))
}

/** One level of unwrapping: what the value class wraps, and how to get in and out of it. */
private sealed interface Level {
    data class Next(val next: KSType, val step: UnwrapStep) : Level
    data class Unsupported(val message: String) : Level
}

/**
 * Unwraps a single level, by whichever of the two routes applies: the standard library's unsigned
 * types expose a conversion pair, everything else a property plus a constructor or factory.
 */
private fun KSType.unwrapOneLevel(builtIns: KSBuiltIns): Level {
    val declaration = this.declaration as? KSClassDeclaration
        ?: return Level.Unsupported(valueClassUnreadableMessage(this))
    unsignedLevel(declaration, builtIns)?.let { return it }
    return ordinaryLevel(declaration)
}

/**
 * The property-and-constructor route, which is how every value class outside the standard library is
 * reached: read the single property, rebuild through the factory or the constructor.
 */
private fun KSType.ordinaryLevel(declaration: KSClassDeclaration): Level {
    val constructor = declaration.primaryConstructor
        ?: return Level.Unsupported(valueClassUnreadableMessage(this))
    val parameter = constructor.parameters.singleOrNull()
        ?: return Level.Unsupported(valueClassUnreadableMessage(this))
    val propertyName = parameter.name?.asString()
        ?: return Level.Unsupported(valueClassUnreadableMessage(this))
    val qualified = declaration.qualifiedName?.asString()
        ?: return Level.Unsupported(valueClassUnreadableMessage(this))

    val property = declaration.getAllProperties()
        .firstOrNull { it.simpleName.asString() == propertyName }
    if (property == null || !property.isReachableForShim()) {
        // Both say the value cannot be read; they differ in what the author can do about it. Telling
        // someone to widen `Duration.rawValue` sends them to a file they cannot edit.
        return Level.Unsupported(
            if (declaration.origin == Origin.KOTLIN) {
                valueClassPrivatePropertyMessage(this, propertyName)
            } else {
                valueClassForeignPropertyMessage(this, propertyName)
            })
    }

    val underlying = parameter.type.resolve()
    // Refused rather than carried: the payload generated code carries is this type, and a nullable
    // payload is already forbidden for a domain-primitive. Nullability belongs to the reference at the
    // call site, which is free to be `T?` whatever the value class wraps.
    if (underlying.isMarkedNullable) {
        return Level.Unsupported(valueClassNullablePayloadMessage(this, propertyName))
    }

    val factories = declaration.valueClassFactories(underlying)
    // Reported before ambiguity, and reported at all rather than skipped over: a nullable-returning
    // single-argument companion function is an attempt at a factory, so silently falling through to the
    // constructor would construct around the check it was written to perform.
    val nullableFactory = factories.firstOrNull { it.returnType?.resolve()?.isMarkedNullable == true }
    if (nullableFactory != null) {
        return Level.Unsupported(
            valueClassNullableFactoryMessage(this, nullableFactory.simpleName.asString()))
    }
    if (factories.size > 1) {
        return Level.Unsupported(
            valueClassAmbiguousFactoryMessage(this, factories.map { it.simpleName.asString() }))
    }
    // A factory wins whenever one exists, even with the constructor in reach. The idiom it exists for
    // validates inside the factory, and constructing around that check would let generated code mint
    // values the author declared impossible.
    val factory = factories.singleOrNull()
    val builder = when {
        factory != null -> "$qualified.${factory.simpleName.asString()}"
        constructor.isReachableForShim() -> qualified
        else -> return Level.Unsupported(valueClassUnconstructableMessage(this))
    }
    return Level.Next(underlying, UnwrapStep.Property(propertyName, builder))
}

/**
 * The unsigned-type level, or `null` when [declaration] is not one of them.
 *
 * The underlying type comes from [KSBuiltIns] rather than from the declaration, because it is never
 * mentioned in any signature the shim could reach — `UInt.data` is internal to the standard library.
 */
private fun unsignedLevel(
    declaration: KSClassDeclaration,
    builtIns: KSBuiltIns
): Level? {
    val fqn = declaration.qualifiedName?.asString() ?: return null
    val conversion = UNSIGNED_CONVERSIONS[fqn] ?: return null
    return Level.Next(
        conversion.underlying(builtIns),
        UnwrapStep.Conversion(conversion.unwrap, conversion.wrap))
}

/**
 * Single-argument companion functions that return the value class and take its underlying type — the
 * same signature rule Lazyval applies to a domain-primitive's factory, so the two cannot disagree
 * about what counts as one.
 *
 * A nullable return still matches, so that [valueClassNullableFactoryMessage] can name it. Only a
 * domain-primitive's factory may answer `null`; this one is rejected by the caller of this function,
 * not hidden from it.
 */
private fun KSClassDeclaration.valueClassFactories(underlying: KSType): List<KSFunctionDeclaration> =
    declarations
        .filterIsInstance<KSClassDeclaration>()
        .firstOrNull { it.isCompanionObject }
        ?.declarations
        ?.filterIsInstance<KSFunctionDeclaration>()
        ?.filter { function ->
            function.isPublic() &&
                    function.parameters.size == 1 &&
                    function.returnType?.resolve()?.makeNotNullable() == asStarProjectedType() &&
                    function.parameters[0].type.resolve().makeNotNullable() == underlying
        }
        ?.toList()
        .orEmpty()

/**
 * The shim is Kotlin, so `public` is reachable and `private`/`protected` are not. `internal` is
 * reachable only from the module that declares it — which is why the standard library's unsigned types
 * need the conversion route rather than their internal property.
 */
private fun KSDeclaration.isReachableForShim(): Boolean =
    when (getVisibility()) {
        Visibility.PUBLIC -> true
        Visibility.INTERNAL -> origin == Origin.KOTLIN
        else -> false
    }
