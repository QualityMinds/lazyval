package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * `UInt` and its siblings are value classes, and the ones that need the conversion route rather than
 * the property route: `UInt.data` and `UInt`'s constructor are both `internal` to the standard library,
 * so nothing outside it can read or build one that way. `toInt()` / `toUInt()` are the only way in.
 *
 * Also the classpath case — the declaration comes from the stdlib rather than the compilation unit, so
 * the rule has to recognise a value class without `Modifier.VALUE`, which KSP reports only for source.
 */
@LazyValue
class UnsignedPayload(val count: UInt)
