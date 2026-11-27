package evaluation.k12

import com.qualityminds.lazyval.LazyValue

typealias Count = UInt

/** `UInt` through a type alias: `edge/UnsignedPayload.kt` without the alias is unwrapped through `toInt()`. */
@LazyValue
data class Stock(val value: Count)
