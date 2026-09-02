package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * `UInt` and its siblings are value classes, so an unsigned payload mangles exactly like
 * `ValueClassPayload` — `getCount-pVg5ArA()` over an erased `int`. Nothing in the source hints at it,
 * which makes this the shape most likely to surprise someone.
 *
 * Worth its own scenario because the declaration comes from the stdlib rather than the compilation
 * unit: the rule has to read `Modifier.VALUE` off a classpath declaration, not just a local one.
 */
@LazyValue
class UnsignedPayload(val count: UInt)
