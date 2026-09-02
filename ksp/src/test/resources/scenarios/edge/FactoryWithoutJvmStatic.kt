package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * The idiomatic Kotlin factory, with no Java-interop annotation on it at all. Kotlin compiles it onto
 * the companion class alone, so generated Java reaches it through the companion's field —
 * `FactoryWithoutJvmStatic.Companion.of(value)` — while generated Kotlin calls it straight off the type.
 *
 * Worth pinning because `@JvmStatic` used to be required here and said so nowhere: every other Kotlin
 * factory scenario in the testkit carries it, and a type without it produced Java that would not compile.
 */
@LazyValue
class FactoryWithoutJvmStatic private constructor(val value: String) {

    companion object {

        fun of(value: String): FactoryWithoutJvmStatic = FactoryWithoutJvmStatic(value)
    }
}
