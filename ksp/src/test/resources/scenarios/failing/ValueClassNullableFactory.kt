package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * `Unsure.of` returns `Unsure?`, which a domain-primitive's own factory is allowed to do: its caller
 * holds the result and can hold a `null`. This one is called from inside the re-wrapping expression,
 * where the enclosing constructor wants an `Unsure` — so the `null` has nowhere to go.
 *
 * A public constructor would not rescue it. Lazyval prefers a factory precisely because it validates,
 * and reaching past this one for the constructor would mint the values `of` was written to reject.
 */
@LazyValue
class ValueClassNullableFactory(val amount: Unsure)

@JvmInline
value class Unsure private constructor(val raw: Long) {

    companion object {

        fun of(raw: Long): Unsure? = if (raw >= 0) Unsure(raw) else null
    }
}
