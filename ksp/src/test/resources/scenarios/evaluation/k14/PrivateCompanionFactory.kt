package evaluation.k14

import com.qualityminds.lazyval.LazyValue

/** A public factory inside a `private companion object`: the function is public, the object holding it is not. */
@LazyValue
class PrivateCompanionFactory private constructor(val value: String) {
    private companion object {
        fun of(value: String): PrivateCompanionFactory = PrivateCompanionFactory(value.trim())
    }
}
