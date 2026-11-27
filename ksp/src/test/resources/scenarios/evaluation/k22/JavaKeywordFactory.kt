package evaluation.k22

import com.qualityminds.lazyval.LazyValue

/** A factory whose Kotlin name is a Java keyword: legal Kotlin, not callable by that name from Java. */
@LazyValue
class JavaKeywordFactory private constructor(val value: String) {
    companion object {
        fun new(value: String): JavaKeywordFactory = JavaKeywordFactory(value.trim())
    }
}
