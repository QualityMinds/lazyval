package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * Properties generated code cannot reach must not count towards the "exactly one property" rule:
 * neither `cachedLength` nor `normalized` is readable from another package, so `value` stays the
 * unambiguous payload instead of the class being rejected as a non-simple ValueType.
 */
@LazyValue
class ClassWithPrivateExtraProperty(val value: String) {

    private val cachedLength: Int = value.length
    internal val normalized: String = value.lowercase()

    override fun toString(): String =
        "ClassWithPrivateExtraProperty($value, $cachedLength, $normalized)"
}
