package scenarios.kotlin

import com.qualityminds.lazyval.LazyValue

@LazyValue
class NullableQuantity private constructor(val value: Int) {
    init {
        require(value >= 0) { "Quantity must be greater than 0" }
    }

    companion object {
        @JvmStatic
        fun ofNullable(value: Int?): NullableQuantity?{
            return value?.let{
                NullableQuantity(value)
            }
        }
    }
}