package evaluation.k12

import com.qualityminds.lazyval.LazyValue

@JvmInline
value class Cents(val amount: Long)

typealias Money = Cents

/** A value-class payload reached through a type alias, which has to be unwrapped like the value class itself. */
@LazyValue
data class Price(val value: Money)
