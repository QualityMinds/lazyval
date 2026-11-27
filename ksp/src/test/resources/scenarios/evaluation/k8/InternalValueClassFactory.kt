package evaluation.k8

import com.qualityminds.lazyval.LazyValue

/**
 * A value class that keeps construction to its module and validates in an `internal` factory. Both are
 * reachable from generated code in this module; only the factory enforces the range.
 */
@JvmInline
value class Rate internal constructor(val percent: Int) {
    companion object {
        internal fun of(percent: Int): Rate {
            require(percent in 0..100) { "Rate must be a percentage" }
            return Rate(percent)
        }
    }
}

@LazyValue
data class Discount(val value: Rate)
