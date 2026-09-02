package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * `InternalFactoryWithJvmName` without `@JvmStatic`, which is the idiomatic way to write it. Both
 * routings apply at once: `@JvmName` keeps the module suffix off the name, and the missing `@JvmStatic`
 * leaves the function on the companion, so generated Java calls
 * `InternalFactoryOnCompanion.Companion.of(value)`.
 *
 * Worth its own scenario because the two features are resolved in different places — the name by
 * `Resolver.getJvmName`, the owner by looking for `@JvmStatic` — and a type needing both would not be
 * covered by either on its own.
 */
@LazyValue
class InternalFactoryOnCompanion private constructor(val value: String) {

    companion object {

        @JvmName("of")
        internal fun of(value: String): InternalFactoryOnCompanion = InternalFactoryOnCompanion(value)
    }
}
