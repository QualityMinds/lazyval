package com.qualityminds.lazyval.ksp.spi

import com.qualityminds.lazyval.ksp.internal.AccessPlan
import com.qualityminds.lazyval.naming.DotName
import org.jetbrains.annotations.ApiStatus

/**
 * Kotlin expressions that read a domain-primitive's payload and rebuild it.
 *
 * Reached through [ValidatedKspGeneratorElement.kotlin]. A generator asks for a whole expression and
 * writes it out; it never assembles one from an accessor name.
 *
 * ```
 * FunSpec.builder("encode")
 *     .addStatement("return %L", element.kotlin.read("value"))
 * ```
 *
 * That indirection is worth more in Kotlin than the accessor name suggests, because three separate
 * things move the spelling:
 *
 * - The payload is not always a property. An explicit accessor function has to be *called*, so the
 *   expression ends in `()` — a KSP question, answered once during validation.
 * - A `value class` payload does not exist at runtime. Reading it means walking down to the type it
 *   wraps, and rebuilding it means walking back up through the wrapper's own factory, so a validating
 *   factory is never bypassed. Unsigned types are value classes too, so `UInt` contributes a
 *   `.toInt()` on the way down and a `.toUInt()` on the way back.
 * - Null-safety has two shapes for one intent: an ordinary payload needs `x?.money`, while a chain
 *   needs `x?.let { … }`, since a safe call would guard only the first hop and dereference the rest.
 *
 * [readOrNull] and [createOrNull] exist so a generator emitting a nullable conversion — a JPA
 * converter, a Cassandra codec — does not have to make that last choice, or know that there was one.
 *
 * The rules a `value class` payload has to satisfy are documented under
 * [value class payloads](https://qualityminds.github.io/lazyval/lazyval/main/rules.html#value-class).
 */
@ApiStatus.Experimental
class KotlinPayload internal constructor(private val plan: AccessPlan) {

    /** Reads the payload out of [instance], unwrapped to [ValidatedKspGeneratorElement.payloadType]. */
    fun read(instance: String): PayloadExpr = plan.kotlinRead(instance)

    /** [read] for a nullable [instance], yielding `null` when it is. */
    fun readOrNull(instance: String): PayloadExpr = plan.kotlinReadOrNull(instance)

    /** Rebuilds the domain-primitive from [payload], a `payloadType` value. */
    fun create(payload: String): PayloadExpr = plan.kotlinCreate(payload)

    /** [create] for a nullable [payload], yielding `null` when it is. */
    fun createOrNull(payload: String): PayloadExpr = plan.kotlinCreateOrNull(payload)
}

/**
 * Java expressions that read a domain-primitive's payload and rebuild it.
 *
 * Reached through [ValidatedKspGeneratorElement.java]. Kotlin declarations do not carry the names Java
 * has to call, and there are three ways they diverge:
 *
 * - `@JvmName` and `internal` both move a member's bytecode name, so the getter is not always
 *   `getMoney()`.
 * - A companion factory without `@JvmStatic` is compiled onto `Companion`, not onto the type, so Java
 *   has to spell `Order.Companion.of(…)`.
 * - A `value class` payload leaves Java nothing to name at all: the accessor's JVM name carries a
 *   signature hash and the enclosing constructor turns private. Lazyval emits a
 *   [JavaAccessShim] and these expressions route through it.
 *
 * Guessing any of the three is what used to make generated Java fail to compile. A generator that asks
 * here never has to know which case it is in.
 */
@ApiStatus.Experimental
class JavaPayload internal constructor(private val plan: AccessPlan) {

    /** Reads the payload out of [instance] — through the accessor, or through the shim. */
    fun read(instance: String): PayloadExpr = plan.javaRead(instance)

    /** Rebuilds the domain-primitive from [payload] — constructor, factory, or shim. */
    fun create(payload: String): PayloadExpr = plan.javaCreate(payload)
}

/**
 * A generated expression, with the type names it mentions kept apart from its text.
 *
 * Keeping them apart is the whole point: a generator that manages its own imports needs to know which
 * types an expression names and where, and passing that back as text would leave it to guess. Both
 * halves are available without the SPI having to know anything about JavaPoet or KotlinPoet — it
 * describes the expression, and the generator renders it.
 *
 * Use [asSource] to write the expression as-is, or [asFormat] to hand the type names to a code writer.
 */
@ApiStatus.Experimental
class PayloadExpr internal constructor(internal val parts: List<Part>) {

    internal sealed interface Part {
        data class Text(val text: String) : Part
        data class Type(val name: DotName, val source: String) : Part
    }

    /**
     * The expression as source text, every type it names spelled out: nested for a Kotlin expression,
     * whose file imports the type and writes `Ids.ProductId`, and canonical for a Java one, so it
     * compiles with no import at all.
     *
     * Also what [toString] returns, so an expression can go straight into a template:
     *
     * ```
     * addStatement("return ${element.kotlin.createOrNull("dbValue")}")
     * ```
     */
    fun asSource(): String = parts.joinToString("") {
        when (it) {
            is Part.Text -> it.text
            is Part.Type -> it.source
        }
    }

    /**
     * The expression with [typeSlot] in place of every type it names, and those types in the order
     * they appear — the two things JavaPoet's `$T` and KotlinPoet's `%T` need in order to add the
     * imports themselves.
     *
     * ```
     * val (format, types) = element.java.create("value").asFormat("\$T")
     * val args = types.map { name ->
     *     ClassName.get(name.packageName(), name.simpleNames().first(),
     *                   *name.simpleNames().drop(1).toTypedArray())
     * }
     * methodBuilder.addStatement("return $format", *args.toTypedArray())
     * ```
     */
    fun asFormat(typeSlot: String): Formatted = Formatted(
        parts.joinToString("") { if (it is Part.Type) typeSlot else (it as Part.Text).text },
        parts.filterIsInstance<Part.Type>().map { it.name })

    /** @see asSource */
    override fun toString(): String = asSource()

    /**
     * [asFormat]'s two halves. Destructures, so a call site can name both at once.
     *
     * @param format the expression, with a slot wherever a type belongs
     * @param types the types those slots stand for, in the order the slots appear
     */
    @ConsistentCopyVisibility
    data class Formatted internal constructor(val format: String, val types: List<DotName>)
}
