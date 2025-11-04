package test

import de.qualityminds.lazyval.LazyValue

@LazyValue
class NullableQuantity private constructor(val value: Int) {
    init {
        require(value >= 0) { "Quantity must be greater than 0" }
    }

    companion object {
        fun ofNullable(value: Int?): NullableQuantity?{
            return value?.let{
                NullableQuantity(value)
            }
        }
    }
}