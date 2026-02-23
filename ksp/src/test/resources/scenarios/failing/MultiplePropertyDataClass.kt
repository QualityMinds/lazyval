package scenarios.failing

import com.qualityminds.lazyval.LazyValue

@LazyValue
data class MultiplePropertyDataClass(val value: Int, val value2: String) {
}