package scenarios.jpa

import com.qualityminds.lazyval.LazyValue
import jakarta.persistence.Transient

/**
 * Derived state marked with JPA's `@Transient`. Since the annotation targets both `FIELD` and
 * `METHOD`, Kotlin puts it on the backing field. Without transient handling the second property
 * would trip the "one non-transient property" check.
 */
@LazyValue
class JpaTransientProperty private constructor(val value: String) {

    @Transient
    val derivedLength: Int = value.length

    companion object {
        @JvmStatic
        fun of(value: String): JpaTransientProperty {
            require(value.isNotBlank()) { "value cannot be blank" }
            return JpaTransientProperty(value)
        }
    }
}
