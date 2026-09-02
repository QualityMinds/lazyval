package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * `FactoryWithoutJvmStatic` with the companion given a name, which is also the name of the field Java
 * reaches it through: `FactoryOnNamedCompanion.Factory.of(value)`, not `.Companion.of(value)`.
 *
 * The distinction only exists on the Java side — in Kotlin the companion's name is optional at the call
 * site either way — so a hard-coded `Companion` would pass every Kotlin-only scenario and fail here.
 */
@LazyValue
class FactoryOnNamedCompanion private constructor(val value: String) {

    companion object Factory {

        fun of(value: String): FactoryOnNamedCompanion = FactoryOnNamedCompanion(value)
    }
}
