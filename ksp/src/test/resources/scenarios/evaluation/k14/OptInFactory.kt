package evaluation.k14

import com.qualityminds.lazyval.LazyValue

@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
annotation class UnsafeCreation

/** A factory gated behind an opt-in that is an error to ignore; generated code does not opt in. */
@LazyValue
class OptInFactory private constructor(val value: String) {
    companion object {
        @UnsafeCreation
        fun of(value: String): OptInFactory = OptInFactory(value)
    }
}
