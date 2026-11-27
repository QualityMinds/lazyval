package evaluation.k16

import com.qualityminds.lazyval.LazyValue

@JvmInline
value class Amount(val v: Int)

/**
 * A value-class payload and a factory that answers `null`: each is supported on its own
 * (`edge/ValueClassPayload.kt`, `edge/NullableFactoryPayload.kt`), the combination reaches the Java-access shim.
 */
@LazyValue
class Qty private constructor(val value: Amount) {
    companion object {
        fun of(value: Amount): Qty? = if (value.v == 0) null else Qty(value)
    }
}
