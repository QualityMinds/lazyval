package scenarios.jpa

import com.qualityminds.lazyval.LazyValue
import jakarta.persistence.Transient

/**
 * Counterpart to `JpaTransientProperty`: the explicit `@get:` use-site target puts JPA's
 * `@Transient` on the getter rather than the backing field, so the validator has to look there too.
 */
@LazyValue
class JpaTransientGetter private constructor(val value: String) {

    @get:Transient
    val derivedLength: Int = value.length

    companion object {
        @JvmStatic
        fun of(value: String): JpaTransientGetter {
            require(value.isNotBlank()) { "value cannot be blank" }
            return JpaTransientGetter(value)
        }
    }
}
