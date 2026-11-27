package evaluation.k15

import com.qualityminds.lazyval.LazyValue

/** A parameterized payload type. */
@LazyValue
data class Tags(val value: List<String>)
