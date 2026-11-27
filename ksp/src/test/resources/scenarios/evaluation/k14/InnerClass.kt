package evaluation.k14

import com.qualityminds.lazyval.LazyValue

/** An `inner` class needs an instance of its outer class to be constructed, which generated code does not have. */
class Holder {
    @LazyValue
    inner class InnerClass(val value: String)
}
