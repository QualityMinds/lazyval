package evaluation.k14

import com.qualityminds.lazyval.LazyValue

/** A `suspend` factory can only be called from a coroutine; generated converters and mappers are not one. */
@LazyValue
class SuspendFactory private constructor(val value: String) {
    companion object {
        suspend fun of(value: String): SuspendFactory = SuspendFactory(value)
    }
}
