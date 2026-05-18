package com.qualityminds.lazyval.integration.domain

import com.qualityminds.lazyval.LazyValue
import java.time.LocalDate

@LazyValue
data class OrderDate(val value: LocalDate){
    init {
        val now = LocalDate.now()
        require(value.isBefore(now) || value == now) { "Order date must not be in the future" }
    }

    companion object {
        @JvmStatic
        fun now(): OrderDate = OrderDate(LocalDate.now())
    }
}
