package test

import com.qualityminds.lazyval.LazyValue

@LazyValue
class OrdnerNumber(val value: String) {
    init {
        require(value.matches(regex)) {
            "Invalid OrdnerNumber format! Expected format: XXX-XXXX"
        }
    }

    companion object {
        private val regex = Regex("^\\d{3}-\\d{4}$")
    }
}