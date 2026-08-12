package test

import com.qualityminds.lazyval.LazyValue
// tag::docu[]
@LazyValue
// tag::docu-motivation[]
data class Quantity(val value: Int){
    init {
        require(value > 0) { "Quantity must be greater than 0" }
    }
}
// end::docu-motivation[]
// end::docu[]
