package evaluation.k2

import com.qualityminds.lazyval.LazyValue

/** The module's own domain-primitive, next to the external `java.time.Year`. */
@LazyValue
data class Email(val value: String)
