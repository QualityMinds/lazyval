package scenarios.edge

import com.qualityminds.lazyval.LazyValue

@LazyValue
class IsbnWithAccessor private constructor(private val value: String) {
    fun value(): String = value

    companion object {
        @JvmStatic
        fun parse(value: String): IsbnWithAccessor {
            require(value.isNotBlank()) { "ISBN cannot be blank" }
            return IsbnWithAccessor(value)
        }
    }
}