package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * Moving the array into a value class changes nothing: generated code carries what the value class wraps
 * in place of the value class, so `Tags` would hand it the array payload `ArrayPayload` is refused for —
 * of any element type, not only bytes.
 */
@LazyValue
class ValueClassArrayPayload(val tags: Tags)

@JvmInline
value class Tags(val names: Array<String>)
