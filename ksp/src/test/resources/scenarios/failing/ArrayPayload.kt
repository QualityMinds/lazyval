package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * An array cannot carry a value: it is mutable, and even a data class compares it by identity. Binary data
 * is what reaches for one, hence the advice to wrap a String encoding of the bytes instead.
 */
@LazyValue
class ArrayPayload(val value: ByteArray)
