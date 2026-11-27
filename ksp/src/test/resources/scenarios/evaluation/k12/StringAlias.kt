package evaluation.k12

import com.qualityminds.lazyval.LazyValue

typealias Code = String

/** A plain `String` payload spelled through a type alias, which only Kotlin knows about. */
@LazyValue
data class Coded(val value: Code)
