package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * A value class may hide the very value it wraps, and Kotlin allows it. Nothing outside `Opaque` can
 * read `raw`, so there is no unwrapping path and therefore nothing generated code could carry in its
 * place.
 *
 * The diagnostic names the value class rather than the domain-primitive: it is `Opaque`'s own contract
 * that is in the way, and blaming `ValueClassPrivateProperty` would send the author to the wrong file.
 */
@LazyValue
class ValueClassPrivateProperty(val token: Opaque)

@JvmInline
value class Opaque(private val raw: Long)
