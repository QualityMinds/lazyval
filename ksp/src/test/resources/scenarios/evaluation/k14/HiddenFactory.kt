package evaluation.k14

import com.qualityminds.lazyval.LazyValue

/**
 * `DeprecationLevel.HIDDEN` removes the factory from Kotlin's resolution while it still matches by signature. The
 * constructor is private, so there is no other creator to fall back to.
 */
@LazyValue
class HiddenFactory private constructor(val value: String) {
    companion object {
        @Deprecated("use something else", level = DeprecationLevel.HIDDEN)
        fun of(value: String): HiddenFactory = HiddenFactory(value)
    }
}
