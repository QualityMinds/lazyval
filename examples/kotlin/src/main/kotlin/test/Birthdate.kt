package test

import com.qualityminds.lazyval.LazyValue
import java.time.LocalDate

// tag::docu[]
@LazyValue
class Birthdate private constructor(val value: String) {

    // derived state computed from `value` — excluded from validation by @Transient
    @Transient
    val asLocalDate: LocalDate = LocalDate.parse(value)

    companion object {
        @JvmName("fromString") // Lazyval can handle JvmName as well
        fun of(isoDate: String): Birthdate {
            return Birthdate(isoDate)
        }
    }
}
// end::docu[]
