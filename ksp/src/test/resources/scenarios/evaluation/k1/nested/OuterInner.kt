package evaluation.k1.nested

import com.qualityminds.lazyval.LazyValue

/** Top-level type whose simple name is what the nested `Outer.Inner` flattens to. */
@LazyValue
data class OuterInner(val value: String)
