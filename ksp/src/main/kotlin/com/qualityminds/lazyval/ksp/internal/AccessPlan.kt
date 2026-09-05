package com.qualityminds.lazyval.ksp.internal

import com.qualityminds.lazyval.ksp.spi.JavaAccessShim
import com.qualityminds.lazyval.ksp.spi.PayloadExpr
import com.qualityminds.lazyval.naming.DotName

/**
 * Everything needed to spell the expressions that read a domain-primitive's payload and rebuild it —
 * and nothing that needs a compiler to look at.
 *
 * Deliberately made of plain strings and [DotName]s. The decisions that need KSP (which member is the
 * accessor, what its bytecode name is, whether the payload is a value class) are taken once during
 * validation; what is left is string assembly, which belongs somewhere it can be asserted directly.
 * Everything below used to be spelled inside
 * [ValidatedKspGeneratorElement][com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement],
 * where the only way to check an expression was to compile a scenario and read the generated file.
 *
 * Each method returns a [PayloadExpr] rather than a string, so the type names an expression mentions
 * stay separable from its text; that is what lets a generator hand them to JavaPoet or KotlinPoet as
 * imports. The two facades in
 * [PayloadAccess][com.qualityminds.lazyval.ksp.spi.KotlinPayload] are thin wrappers over these.
 *
 * @param kotlinAccessor Kotlin spelling that reads the *declared* payload off an instance — `"money"`
 *                       for a property, `"money()"` for an accessor function. Which of the two applies
 *                       is a KSP question, so it arrives already answered.
 * @param kotlinFactory simple name of the domain-primitive's factory, or `null` to call its constructor
 * @param name the domain-primitive's own name
 * @param javaAccessorName bytecode name of the accessor Java output has to call, which `@JvmName` and
 *                         `internal` both move away from the Kotlin declaration
 * @param javaFactoryPath dot-path from the Java type down to the factory, or `null` for the constructor
 * @param unwrapping the value-class chain, outermost first; empty when the payload is carried as
 *                   declared. Non-empty exactly when [shim] is set — both mean "value class payload".
 * @param shim the generated Kotlin object generated Java goes through, or `null` when it can go direct
 */
internal data class AccessPlan(
    val kotlinAccessor: String,
    val kotlinFactory: String?,
    val name: DotName,
    val javaAccessorName: String,
    val javaFactoryPath: String?,
    val unwrapping: List<UnwrapStep>,
    val shim: JavaAccessShim?
) {
    /** Whether the payload is a `value class`, and so reached through [unwrapping] rather than directly. */
    val isUnwrapped: Boolean get() = unwrapping.isNotEmpty()

    /**
     * Kotlin expression reading the payload out of [instance], unwrapped all the way down.
     *
     * Folds outermost-first, because that is the order the reads happen in: `order.money` yields the
     * value class, and only then can `.toLong()` be asked of it.
     */
    fun kotlinRead(instance: String): PayloadExpr = payloadExpr {
        text(unwrapping.fold("$instance.$kotlinAccessor") { expression, step -> step.read(expression) })
    }

    /**
     * [kotlinRead] for a nullable receiver, yielding `null` when [instance] is.
     *
     * A safe call carries the whole chain only if the chain is a single member access. Once it is two,
     * `instance?.money.toLong()` would dereference the `null` it just guarded, so the chain moves inside
     * a `let` where the receiver is already known to be there.
     */
    fun kotlinReadOrNull(instance: String): PayloadExpr =
        if (isUnwrapped) {
            payloadExpr {
                text("$instance?.let { ")
                expr(kotlinRead("it"))
                text(" }")
            }
        } else {
            payloadExpr { text("$instance?.$kotlinAccessor") }
        }

    /**
     * Kotlin expression rebuilding the domain-primitive from an unwrapped [value].
     *
     * Folds innermost-first — the mirror image of [kotlinRead], since the chain has to be reassembled
     * from the inside before the domain-primitive can be built around it.
     */
    fun kotlinCreate(value: String): PayloadExpr =
        kotlinConstruct(unwrapping.foldRight(value) { step, expression -> step.build(expression) })

    /**
     * [kotlinCreate] for a nullable [value], yielding `null` when it is.
     *
     * A `let` in both cases, unwrapped or not: Kotlin has no safe-call form for passing a nullable to a
     * constructor, so there is no shorter spelling to fall back to.
     */
    fun kotlinCreateOrNull(value: String): PayloadExpr = payloadExpr {
        text("$value?.let { ")
        expr(kotlinCreate("it"))
        text(" }")
    }

    /**
     * Kotlin expression building the domain-primitive around [value] as it stands, with no unwrapping
     * chain applied.
     *
     * [DotName.nestedName] rather than the qualified name: this lands in Kotlin output, where the
     * generated file imports the domain-primitive and spells it `Ids.ProductId`.
     */
    private fun kotlinConstruct(value: String): PayloadExpr = payloadExpr {
        type(name, name.nestedName())
        if (kotlinFactory != null) {
            text(".$kotlinFactory")
        }
        text("($value)")
    }

    /**
     * Java expression reading the payload out of [instance] — the accessor directly, or through [shim]
     * when Kotlin left Java no name to call.
     */
    fun javaRead(instance: String): PayloadExpr =
        if (shim != null) {
            payloadExpr {
                type(shim.name, shim.name.canonicalName())
                text(".${shim.readMember}($instance)")
            }
        } else {
            payloadExpr { text("$instance.$javaAccessorName()") }
        }

    /** Java expression rebuilding the domain-primitive from [value]: shim, factory, or constructor. */
    fun javaCreate(value: String): PayloadExpr =
        if (shim != null) {
            payloadExpr {
                type(shim.name, shim.name.canonicalName())
                text(".${shim.createMember}($value)")
            }
        } else {
            payloadExpr {
                if (javaFactoryPath == null) {
                    text("new ")
                }
                type(name, name.canonicalName())
                if (javaFactoryPath != null) {
                    text(".$javaFactoryPath")
                }
                text("($value)")
            }
        }
}

/** Assembles a [PayloadExpr] out of literal text and the types it names. */
internal fun payloadExpr(build: PayloadExprBuilder.() -> Unit): PayloadExpr =
    PayloadExprBuilder().apply(build).build()

internal class PayloadExprBuilder {
    private val parts = mutableListOf<PayloadExpr.Part>()

    /** Literal source text. Empty text is dropped, so a caller can append unconditionally. */
    fun text(text: String) {
        if (text.isNotEmpty()) parts += PayloadExpr.Part.Text(text)
    }

    /** A type the expression names, with [source] as its spelling when written out as-is. */
    fun type(name: DotName, source: String) {
        parts += PayloadExpr.Part.Type(name, source)
    }

    /** Splices [other] in, keeping its type references separable rather than flattening them to text. */
    fun expr(other: PayloadExpr) {
        parts += other.parts
    }

    fun build(): PayloadExpr = PayloadExpr(parts.toList())
}
