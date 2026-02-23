package com.qualityminds.lazyval.integration

data class CreateOrderDto(val isbn: String, val quantity: Int, val email: String)

data class OrderDto(val id: Long, val isbn: String, val quantity: Int, val email: String)