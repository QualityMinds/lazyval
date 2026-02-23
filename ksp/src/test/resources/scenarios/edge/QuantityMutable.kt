package scenarios.edge

import com.qualityminds.lazyval.LazyValue

@LazyValue
data class QuantityMutable(var value: Int) {
    init {
        require(value >= 0) { "Quantity must be greater than 0" }
    }
}