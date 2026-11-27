package evaluation.k10

import com.qualityminds.lazyval.LazyValue

/**
 * An `internal` domain-primitive, which `rules.adoc` (`[#visibility]`) documents as accepted: a caller outside
 * the module cannot name the type, so nothing hidden is published by the code generated around it.
 */
@LazyValue
internal data class Secret(val value: String)
