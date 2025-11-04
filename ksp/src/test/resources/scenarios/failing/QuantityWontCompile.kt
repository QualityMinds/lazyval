package scenarios.failing

import de.qualityminds.lazyval.LazyValue

@LazyValue
data class QuantityWontCompile(val value: Int)
    init {
    }
}