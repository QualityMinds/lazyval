package evaluation.k14

import com.qualityminds.lazyval.LazyValue

/** A public nested domain-primitive whose enclosing class is private to the file. */
private class Hidden {
    @LazyValue
    data class NestedInPrivateClass(val value: String)
}
