package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * The factory counterpart to `PropertyWithRenamedJvmName`: `@JvmStatic` puts the function on the class,
 * `@JvmName` decides under which name. Generated Java calls `create`, generated Kotlin calls `of` — the
 * same declaration, named twice, and each half of the output uses the name its language can see.
 */
@LazyValue
class FactoryWithRenamedJvmName private constructor(val value: String) {

    companion object {

        @JvmStatic
        @JvmName("create")
        fun of(value: String): FactoryWithRenamedJvmName = FactoryWithRenamedJvmName(value)
    }
}
