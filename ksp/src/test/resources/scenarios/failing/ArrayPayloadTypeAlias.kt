package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * A type alias hides the array from the payload's declared type but not from generated code, which carries
 * a `ByteArray` all the same — so the array rule looks through it.
 */
@LazyValue
class ArrayPayloadTypeAlias(val value: Hash)

typealias Hash = ByteArray
