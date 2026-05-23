package com.qualityminds.lazyval.integration.domain

import com.qualityminds.lazyval.integration.shared.CouponCode
import com.qualityminds.lazyval.integration.shared.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import java.util.*

data class Order(
    val id: UUID,
    val isbn: Isbn,
    val quantity: Quantity,
    val email: EMail,
    val orderDate: OrderDate,
    val couponCode: CouponCode? = null
) {
    companion object {
        fun create(isbn: Isbn, quantity: Quantity, email: EMail, couponCode: CouponCode? = null): Order {
            return Order(UUID.randomUUID(), isbn, quantity, email, OrderDate.now(), couponCode)
        }
    }
}