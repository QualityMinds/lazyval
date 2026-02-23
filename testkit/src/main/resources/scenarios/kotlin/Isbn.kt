package scenarios.kotlin

import com.qualityminds.lazyval.LazyValue

@LazyValue
class Isbn private constructor(val value: String) {
    companion object {
        @JvmStatic
        fun parse(value: String): Isbn {
            require(value.isNotBlank()) { "ISBN cannot be blank" }
            return Isbn(value)
        }
    }
}