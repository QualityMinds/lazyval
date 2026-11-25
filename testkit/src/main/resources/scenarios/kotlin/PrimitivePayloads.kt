package scenarios.kotlin

import com.qualityminds.lazyval.LazyValue

/**
 * One domain-primitive per primitive payload kind, so a single compiler run proves that every arm of a
 * generator's `when` over the kind produces code that compiles.
 */
object PrimitivePayloads {

    @LazyValue
    data class Flag(val value: Boolean)

    @LazyValue
    data class Level(val value: Byte)

    @LazyValue
    data class Rank(val value: Short)

    @LazyValue
    data class Count(val value: Int)

    @LazyValue
    data class Total(val value: Long)

    @LazyValue
    data class Grade(val value: Char)

    @LazyValue
    data class Ratio(val value: Float)

    @LazyValue
    data class Price(val value: Double)
}
