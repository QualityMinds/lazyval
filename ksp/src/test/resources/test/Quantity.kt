package test

import de.qualityminds.lazyval.LazyValue

@LazyValue
data class Quantity(val value: Int) {
    init {
        require(value >= 0) { "Quantity must be greater than 0" }
    }
}