package com.qualityminds.lazyval.integration.shared

/**
 * Wraps a `value class` payload, so every generator has to unwrap it: Kotlin compiles [Amount] away, and
 * neither its mangled accessor nor `Quantity`'s bytecode-private constructor is nameable from Java.
 *
 * The erased type is still `Int`, so nothing downstream changes — the same column, the same JSON, the
 * same DTOs — which is what makes this a test of the unwrapping rather than of a new field.
 *
 * Callers go through [of] rather than a convenience constructor, because there cannot be one: a
 * `Quantity(Int)` overload would erase to the same `<init>(I)V` as the primary constructor and Kotlin
 * rejects the clash. [of] is not a Lazyval factory either — it takes `Int` rather than the [Amount]
 * payload — so generated code still reconstructs through the primary constructor.
 */
data class Quantity(val value: Amount) {

    companion object {

        @JvmStatic
        fun of(value: Int): Quantity = Quantity(Amount.of(value))
    }
}

/**
 * Hides its constructor behind a validating factory, so generated code has to re-wrap through
 * [Amount.of] rather than construct around the check.
 */
@JvmInline
value class Amount private constructor(val value: Int) {

    companion object {

        fun of(value: Int): Amount {
            require(value > 0) { "Quantity must be greater than 0" }
            return Amount(value)
        }
    }
}
