package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * `@JvmField` exposes the backing field directly and suppresses the getter altogether, leaving a public
 * unmangled `value` field and no `getValue()`. Generated code reads the payload through an accessor and
 * has no field path, so there is nothing for it to call.
 *
 * The one JVM-name mismatch that resolving the real name cannot repair — unlike
 * `edge/PropertyWithRenamedJvmName`, where the accessor was moved rather than removed. Widening the
 * property changes nothing either, which is why the message blames neither visibility nor the package
 * boundary. An accessor function is the way out, as `edge/InternalPropertyWithAccessor` shows.
 */
@LazyValue
class JvmFieldPropertyClass(@JvmField val value: String)
