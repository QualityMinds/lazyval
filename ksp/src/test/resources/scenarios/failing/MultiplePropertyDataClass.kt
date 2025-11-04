package scenarios.failing

import de.qualityminds.lazyval.LazyValue

@LazyValue
data class MultiplePropertyDataClass(val value: Int, val value2: String) {
}