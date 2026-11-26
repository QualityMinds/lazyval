package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * Reads like a domain-primitive over a String, but the only constructor takes more than the payload and there is
 * no factory to take its place, so generated code has no way back in from the value it read out.
 */
@LazyValue
class ConstructorTakesMoreThanPayload(value: String, suffix: String) {
    val value: String = value + suffix
}
