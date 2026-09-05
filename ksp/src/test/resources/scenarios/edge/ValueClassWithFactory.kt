package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * The validated-value-class idiom, and the reason the factory has to *win* rather than merely be
 * accepted: `Rate` hides its constructor so that `of` can enforce the range. Generated code re-wraps
 * through `Rate.of`, so a mapper or a deserializer cannot mint a `Rate` the author declared impossible.
 *
 * Both halves carry a factory here — the domain-primitive's and the value class's — so the shim has to
 * compose them: `ValueClassWithFactory.of(Rate.of(payload))`.
 */
@LazyValue
class ValueClassWithFactory private constructor(val rate: Rate) {

    companion object {

        @JvmStatic
        fun of(rate: Rate): ValueClassWithFactory = ValueClassWithFactory(rate)
    }
}

@JvmInline
value class Rate private constructor(val percent: Int) {

    companion object {

        fun of(percent: Int): Rate {
            require(percent in 0..100) { "Rate must be a percentage" }
            return Rate(percent)
        }
    }
}
