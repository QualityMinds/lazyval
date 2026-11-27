package evaluation.k21

import com.qualityminds.lazyval.LazyValue

/** A nested domain-primitive, which generated code has to name as `Ids.ProductId`. */
object Ids {
    @LazyValue
    data class ProductId(val value: String)
}
