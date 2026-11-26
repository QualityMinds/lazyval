package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * Rebuilt through a constructor that takes the payload as `Int?`. A constructor cannot yield `null`, so it has
 * to invent a value for one; the nullable wrapper belongs on a factory returning `NullableConstructorPayload?`,
 * next to a private constructor.
 */
@LazyValue
class NullableConstructorPayload(value: Int?) {

    val value: Int = value ?: 0
}
