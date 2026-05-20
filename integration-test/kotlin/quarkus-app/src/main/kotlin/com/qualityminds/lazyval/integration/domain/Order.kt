package com.qualityminds.lazyval.integration.domain

import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import java.util.UUID

data class Order(
    val id: UUID,
    val isbn: Isbn,
    val quantity: Quantity,
    val email: EMail,
    val orderDate: OrderDate
) {
    companion object {
        @JvmStatic
        fun create(isbn: Isbn, quantity: Quantity, email: EMail): Order =
            Order(UUID.randomUUID(), isbn, quantity, email, OrderDate.now())
    }
}
