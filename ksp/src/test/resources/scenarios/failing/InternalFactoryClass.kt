package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * The factory counterpart to `InternalPropertyClass`, and the same surprise: `internal` is visible to
 * the whole module, generated sources included, yet Kotlin mangles an internal function's JVM name to
 * `of$module` — and the Java half of Lazyval's output has no `of` to call.
 *
 * Contrast an internal *constructor*, which is not mangled and is therefore left alone by the
 * validator, the way an internal class is.
 */
@LazyValue
class InternalFactoryClass private constructor(val value: String) {

    companion object {

        @JvmStatic
        internal fun of(value: String): InternalFactoryClass = InternalFactoryClass(value)
    }
}
