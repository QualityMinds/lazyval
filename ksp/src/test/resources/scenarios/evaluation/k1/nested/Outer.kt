package evaluation.k1.nested

import com.qualityminds.lazyval.LazyValue

/** `Outer.Inner` flattens to `OuterInner`, which is also the simple name of the top-level type next to it. */
class Outer {
    @LazyValue
    data class Inner(val value: String)
}
