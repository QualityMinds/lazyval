package scenarios.failing

import com.qualityminds.lazyval.LazyValue

@LazyValue
data class QuantityWontCompile(val value: Int)
    init {
    }
}