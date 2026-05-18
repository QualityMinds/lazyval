package com.qualityminds.lazyval.integration.domain

import com.qualityminds.lazyval.integration.shared.Isbn
import java.util.UUID

interface OrderRepository {
    fun save(order: Order)
    fun findAll(): List<Order>
    fun getById(id: UUID): Order?
    fun findByIsbn(isbn: Isbn): List<Order>
}
