package evaluation.k1.a

import com.qualityminds.lazyval.LazyValue

/** One of two domain-primitives named `Email`; generators name their output after the simple name. */
@LazyValue
data class Email(val value: String)
