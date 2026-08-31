package scenarios.springdata

import com.qualityminds.lazyval.LazyValue
import org.springframework.data.annotation.Transient

/**
 * Derived state marked with Spring Data's `@Transient`. Note the package:
 * `org.springframework.data.annotation`, not `org.springframework.data`.
 */
@LazyValue
class SpringDataTransientProperty private constructor(val value: String) {

    @Transient
    val derivedLength: Int = value.length

    companion object {
        @JvmStatic
        fun of(value: String): SpringDataTransientProperty {
            require(value.isNotBlank()) { "value cannot be blank" }
            return SpringDataTransientProperty(value)
        }
    }
}
