package scenarios.kotlin

import com.qualityminds.lazyval.LazyValue

/**
 * A domain-primitive whose payload is a Kotlin `value class`, and the shape that catches a generator
 * emitting Java naively.
 *
 * Deliberately factory-less: Kotlin makes the constructor of a type taking a value class private in the
 * bytecode, so Java cannot reach it at all and the generated access shim is the only way back in.
 * `Kelvin` in turn hides its own constructor behind a validating factory, so the shim has to re-wrap
 * through `Kelvin.of` rather than construct around the check.
 */
@LazyValue
class Temperature(val kelvin: Kelvin)

@JvmInline
value class Kelvin private constructor(val degrees: Int) {

    companion object {

        fun of(degrees: Int): Kelvin {
            require(degrees >= 0) { "Temperature must not be below absolute zero" }
            return Kelvin(degrees)
        }
    }
}
