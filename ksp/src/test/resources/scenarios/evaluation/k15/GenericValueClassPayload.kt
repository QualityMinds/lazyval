package evaluation.k15

import com.qualityminds.lazyval.LazyValue

@JvmInline
value class Box<T>(val content: T)

/** A generic value class as payload: what it wraps is the type argument, not a class of its own. */
@LazyValue
data class Boxed(val value: Box<String>)
