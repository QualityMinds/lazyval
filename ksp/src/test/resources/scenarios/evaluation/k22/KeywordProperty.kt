package evaluation.k22

import com.qualityminds.lazyval.LazyValue

/** A payload property whose name is a Kotlin hard keyword, legal when escaped. */
@LazyValue
data class KeywordProperty(val `object`: String)
