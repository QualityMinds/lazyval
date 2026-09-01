package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * The shape of `Isbn` — private constructor, companion factory — with the factory hidden too, so
 * neither route back from the payload is reachable. A factory satisfies the reconstruction rule only
 * if generated code can actually call it; sitting in the companion object is not enough.
 */
@LazyValue
class PrivateFactoryClass private constructor(val value: String) {

    companion object {

        @JvmStatic
        private fun of(value: String): PrivateFactoryClass = PrivateFactoryClass(value)
    }
}
