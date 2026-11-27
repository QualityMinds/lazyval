package evaluation.k12

import com.qualityminds.lazyval.LazyValue

typealias MaybeString = String?

/** A nullable payload, spelled through a type alias: the same type `failing/NullablePayload.kt` is rejected for. */
@LazyValue
data class Maybe(val value: MaybeString)
