package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * The visibility that looks like it should work, and the one rejected on design grounds rather than
 * technical ones: generated code sits in the same module and can reach an internal property perfectly
 * well. What it cannot do is keep the secret — a mapper, a codec, a converter are all public API that
 * read the payload out, so honouring this class would publish the very value `internal` withholds.
 *
 * Contrast `edge/InternalDomainPrimitive`, where the *type* is internal: an outside caller cannot name
 * it, so nothing leaks. And `edge/InternalPropertyWithAccessor`, which is the supported way to have
 * this: keep the property internal and hand Lazyval a public accessor to call instead.
 */
@LazyValue
class InternalPropertyClass(internal val value: String)
