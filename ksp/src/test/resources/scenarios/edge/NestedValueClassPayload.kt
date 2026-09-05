package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * A value class wrapping another value class, which is what `Amount(UInt)` amounts to since `UInt` is
 * one as well. Unwrapping is transitive because the JVM has already flattened the lot: `Inner` and
 * `Outer` both erase away, and `NestedValueClassPayload` is a `long` at runtime.
 *
 * Refusing this would have meant declining a payload whose runtime representation the platform has
 * settled — there is only one type it could map to.
 */
@LazyValue
class NestedValueClassPayload(val outer: Outer)

@JvmInline
value class Inner(val raw: Long)

@JvmInline
value class Outer(val inner: Inner)
