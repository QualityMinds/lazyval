package com.qualityminds.lazyval.integration

import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity

data class CreateOrderDto(val isbn: Isbn, val quantity: Quantity, val email: EMail)

data class OrderDto(val id: Long, val isbn: Isbn, val quantity: Quantity, val email: EMail)