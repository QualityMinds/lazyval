package evaluation.k15

import com.qualityminds.lazyval.LazyValue

/** A generic domain-primitive: its payload type is a type parameter. */
@LazyValue
data class Gen<T : Any>(val value: T)
