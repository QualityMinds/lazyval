package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * `Locked` hides its constructor without offering a factory, so the value can be read but never
 * rebuilt. Unwrapping obliges Lazyval to re-wrap, so half a route is no route.
 *
 * Contrast `edge/ValueClassWithFactory`, which hides its constructor and supplies the way back in.
 */
@LazyValue
class ValueClassUnconstructable(val locked: Locked)

@JvmInline
value class Locked private constructor(val raw: Long) {

    companion object {

        fun create(raw: Long, checked: Boolean): Locked = Locked(raw)
    }
}
