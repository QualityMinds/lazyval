package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * A value class may wrap a nullable type, and Kotlin allows it — but generated code carries what the
 * value class wraps *in place of* the value class, so accepting `Vague` would give Lazyval the
 * nullable payload it refuses everywhere else (see `NullablePayload`).
 *
 * The absence this shape reaches for belongs at the call site instead: `ValueClassNullablePayload?`
 * says the same thing without asking generated code to carry a `Long?`.
 */
@LazyValue
class ValueClassNullablePayload(val amount: Vague)

@JvmInline
value class Vague(val raw: Long?)
