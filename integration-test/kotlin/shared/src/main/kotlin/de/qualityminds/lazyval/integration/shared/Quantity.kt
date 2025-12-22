package de.qualityminds.lazyval.integration.shared

data class Quantity(val value: Int){
    init {
        require(value > 0) { "Quantity must be greater than 0" }
    }
}
