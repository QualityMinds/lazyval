package scenarios.edge

import com.qualityminds.lazyval.LazyValue
import java.util.Optional

/**
 * A `Char` payload whose factory `of(Char)` has a null-tolerant overload taking `Char?`. Lazyval only matches
 * `of(Char)`, so generated code must call that one. Mirrors the APT scenario `RecordCharFactoryOverload`, where a
 * boxed `Character` argument binds to the `Character` overload; Kotlin picks the most specific overload, so this
 * is expected to hold here.
 *
 * The overload returns `Optional` on purpose: calling it where a `CharFactoryOverload` is expected does not
 * compile, which turns a silent call to the wrong factory into an error a compile-only test can see.
 */
@LazyValue
data class CharFactoryOverload(val value: Char) {

    companion object {

        @JvmStatic
        fun of(value: Char): CharFactoryOverload {
            require(value.isLetter()) { "Grade must be a letter" }
            return CharFactoryOverload(value.uppercaseChar())
        }

        @JvmStatic
        fun of(nullable: Char?): Optional<CharFactoryOverload> = Optional.ofNullable(nullable).map { of(it.toChar()) }
    }
}
