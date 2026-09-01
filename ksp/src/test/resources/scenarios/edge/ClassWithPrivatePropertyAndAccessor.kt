package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * The documented way to keep a property non-public: hide the property and expose a public accessor
 * function. Lazyval calls the function, so the payload stays readable from generated code — the Kotlin
 * counterpart of the Java `Birthdate` shape (private field, public `value()`).
 */
@LazyValue
class ClassWithPrivatePropertyAndAccessor(private val value: String) {

    fun value(): String = value
}
