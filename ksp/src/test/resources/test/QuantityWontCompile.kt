package test

import de.qualityminds.lazyval.LazyValue

@LazyValue
data class QuantityWontCompile(val value: Int)
    init {
    }
}