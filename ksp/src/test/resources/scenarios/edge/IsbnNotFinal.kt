package scenarios.edge

import de.qualityminds.lazyval.LazyValue

@LazyValue
open class IsbnNotFinal private constructor(val value: String) {
    companion object {
        @JvmStatic
        fun parse(value: String): IsbnNotFinal {
            require(value.isNotBlank()) { "ISBN cannot be blank" }
            return IsbnNotFinal(value)
        }
    }
}