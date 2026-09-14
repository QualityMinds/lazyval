package com.qualityminds.lazyval.ksp.spi

import com.qualityminds.lazyval.ksp.internal.AccessPlan
import com.qualityminds.lazyval.naming.DotName
import org.jetbrains.annotations.ApiStatus

/**
 * Kotlin expressions that read a domain-primitive's payload and rebuild it.
 *
 * Reached through [ValidatedKspGeneratorElement.kotlin]. A generator asks for a whole expression and
 * writes it out; it never assembles one from an accessor name. Each call takes the name of a variable
 * in the code being generated, and returns the expression to put there.
 *
 * ```
 * FunSpec.builder("encode")
 *     .addStatement("return %L", element.kotlin.read("value"))
 * ```
 *
 * ## What comes back
 *
 * ```
 * package com.acme
 *
 * data class ProductId(val value: String)
 *
 * value class Money(val amount: Long)       // @JvmInline
 * class Price(val money: Money)
 * ```
 *
 * ```
 * // given id: ProductId, raw: String
 * element.kotlin.read("id")              // id.value
 * element.kotlin.create("raw")           // com.acme.ProductId(raw)
 * element.kotlin.readOrNull("id")        // id?.value
 * element.kotlin.createOrNull("raw")     // raw?.let { com.acme.ProductId(it) }
 *
 * // given price: Price, raw: Long
 * element.kotlin.read("price")           // price.money.amount
 * element.kotlin.create("raw")           // com.acme.Price(com.acme.Money(raw))
 * element.kotlin.readOrNull("price")     // price?.let { it.money.amount }
 * element.kotlin.createOrNull("raw")     // raw?.let { com.acme.Price(com.acme.Money(it)) }
 * ```
 *
 * `Price` carries a `Long`, not a `Money`: a `value class` does not exist at runtime, so
 * [ValidatedKspGeneratorElement.payloadType] is the type it wraps, walked down on the way out and
 * rebuilt on the way back in. Unsigned types are value classes too, reached through their conversion
 * pair — a `Quantity(val value: UInt)` reads `qty.value.toInt()` and rebuilds as
 * `com.acme.Quantity(raw.toUInt())`.
 *
 * A factory is preferred over the constructor wherever one exists, so generated code never mints a
 * value around the checks it makes: give `ProductId` a `companion object { fun of(value: String) }`
 * and `create("raw")` becomes `com.acme.ProductId.of(raw)`.
 *
 * Types arrive spelled in full, because raw statement text carries no import with it. Hand
 * [PayloadExpr.asFormat]'s two halves to KotlinPoet's `%T` instead and the last line above comes out
 * as `raw?.let { Price(Money(it)) }`, with both imports added for you.
 *
 * ## Why ask for the whole expression
 *
 * Three separate things move the spelling, and none of them is visible from an accessor name:
 *
 * - The payload is not always a property. An explicit accessor function has to be *called*, so the
 *   expression ends in `()` — a KSP question, answered once during validation.
 * - A `value class` payload has to be unwrapped and re-wrapped through the wrapper's own factory, as
 *   above, so a validating factory is never bypassed.
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

    /**
     * Reads the payload out of [instance], unwrapped to [ValidatedKspGeneratorElement.payloadType].
     *
     * For an `id: ProductId`, `read("id")` is `id.value`; for a `price: Price`, `price.money.amount`.
     */
    fun read(instance: String): PayloadExpr = plan.kotlinRead(instance)

    /**
     * [read] for a nullable [instance], yielding `null` when it is.
     *
     * For an `id: ProductId?`, `readOrNull("id")` is `id?.value`; for a `price: Price?`,
     * `price?.let { it.money.amount }`.
     */
    fun readOrNull(instance: String): PayloadExpr = plan.kotlinReadOrNull(instance)

    /**
     * Rebuilds the domain-primitive from [payload], a `payloadType` value.
     *
     * For a `raw: String`, `create("raw")` is `com.acme.ProductId(raw)` — or
     * `com.acme.ProductId.of(raw)` where there is a factory, and
     * `com.acme.Price(com.acme.Money(raw))` for a `value class` payload.
     */
    fun create(payload: String): PayloadExpr = plan.kotlinCreate(payload)

    /**
     * [create] for a nullable [payload], yielding `null` when it is.
     *
     * For a `raw: String?`, `createOrNull("raw")` is `raw?.let { com.acme.ProductId(it) }`, and
     * `raw?.let { com.acme.Price(com.acme.Money(it)) }` for a `value class` payload.
     */
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
 *   signature hash and the enclosing constructor turns private. Lazyval emits a [JavaAccessShim] and
 *   these expressions route through it.
 *
 * Guessing any of the three is what used to make generated Java fail to compile. A generator that asks
 * here never has to know which case it is in.
 *
 * ## What comes back
 *
 * ```
 * package com.acme
 *
 * data class ProductId(val value: String)
 *
 * value class Money(val amount: Long)       // @JvmInline
 * class Price(val money: Money)
 * ```
 *
 * ```
 * // given ProductId id, String raw
 * element.java.read("id")        // id.getValue()
 * element.java.create("raw")     // new com.acme.ProductId(raw)
 *
 * // given Price price, long raw
 * element.java.read("price")     // com.acme.PriceJvmAccess.money(price)
 * element.java.create("raw")     // com.acme.PriceJvmAccess.of(raw)
 * ```
 *
 * `PriceJvmAccess` is the [JavaAccessShim] generated next to `Price`: an `internal object` whose
 * members are ordinary Kotlin, named after the payload property and `of`, trading in the `long` the
 * value class erases to.
 *
 * The other two divergences are name-only, and the expression carries whatever Kotlin compiled.
 * `@get:JvmName("payload")` on the payload makes `read("id")` read `id.payload()`, while
 * [KotlinPayload.read] still reads `id.value`; a factory on a companion is
 * `com.acme.EMail.Companion.of(raw)` without `@JvmStatic` and `com.acme.EMail.of(raw)` with it.
 *
 * Types arrive spelled canonically, so the expression compiles with no import at all. Hand
 * [PayloadExpr.asFormat]'s two halves to JavaPoet's `$T` instead and it adds them itself.
 */
@ApiStatus.Experimental
class JavaPayload internal constructor(private val plan: AccessPlan) {

    /**
     * Reads the payload out of [instance] — through the accessor, or through the shim.
     *
     * For an `id: ProductId`, `read("id")` is `id.getValue()`; for a `price: Price`,
     * `com.acme.PriceJvmAccess.money(price)`.
     */
    fun read(instance: String): PayloadExpr = plan.javaRead(instance)

    /**
     * Rebuilds the domain-primitive from [payload] — constructor, factory, or shim.
     *
     * For a `raw: String`, `create("raw")` is `new com.acme.ProductId(raw)` — or
     * `com.acme.ProductId.Companion.of(raw)` where there is a factory without `@JvmStatic`, and
     * `com.acme.PriceJvmAccess.of(raw)` through the shim.
     */
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
 * It is deliberately neither a `String` nor a `CodeBlock`: a generator may be driving a code writer or
 * filling in a plain template, and the two want different things out of the same expression. Transform
 * it once, at that boundary:
 *
 * - Raw text — a string template, a template engine, anything that concatenates: [asSource], or
 *   [toString] for interpolation. Self-contained, every type spelled canonically, so the text carries
 *   its own resolution and needs no import. A `%T` left in raw text stays a `%T`; nothing expands it.
 * - A code writer — KotlinPoet, JavaPoet: [asFormat] returns the format and the types its slots stand
 *   for, which are exactly a `CodeBlock`'s two arguments. Map each [DotName] to a `ClassName` and the
 *   writer adds the imports, so the statement reads `Price(Money(it))` rather than the qualified form.
 *
 * Both compile. The choice is only whether a type arrives as an import or spelled out in the statement.
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
