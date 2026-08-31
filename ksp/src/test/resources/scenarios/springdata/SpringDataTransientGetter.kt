package scenarios.springdata

import com.qualityminds.lazyval.LazyValue
import org.springframework.data.annotation.Transient

/**
 * Counterpart to `SpringDataTransientProperty`: the explicit `@get:` use-site target puts Spring
 * Data's `@Transient` on the getter rather than the backing field.
 */
@LazyValue
class SpringDataTransientGetter private constructor(val value: String) {

    @get:Transient
    val derivedLength: Int = value.length

    companion object {
        @JvmStatic
        fun of(value: String): SpringDataTransientGetter {
            require(value.isNotBlank()) { "value cannot be blank" }
            return SpringDataTransientGetter(value)
        }
    }
}
