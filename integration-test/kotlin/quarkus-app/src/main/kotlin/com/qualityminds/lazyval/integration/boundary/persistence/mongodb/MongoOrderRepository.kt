package com.qualityminds.lazyval.integration.boundary.persistence.mongodb

import com.qualityminds.lazyval.integration.domain.Order
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import io.smallrye.common.annotation.Identifier
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
@Identifier("mongo")
class MongoOrderRepository @Inject constructor(
    private val mapper: MongoMapper
) : OrderRepository {

    override fun save(order: Order) {
        mapper.toDB(order).persist()
    }

    override fun findAll(): List<Order> =
        mapper.toDomain(MongoOrder.listAll()).sortedBy { it.isbn.value }

    override fun getById(id: UUID): Order? =
        MongoOrder.findById(id)?.let { mapper.toDomain(it) }

    override fun findByIsbn(isbn: Isbn): List<Order> =
        mapper.toDomain(MongoOrder.list("isbn", isbn))
}
