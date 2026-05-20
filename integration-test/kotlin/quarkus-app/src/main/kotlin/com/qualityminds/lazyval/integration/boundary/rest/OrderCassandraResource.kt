package com.qualityminds.lazyval.integration.boundary.rest

import com.qualityminds.lazyval.integration.boundary.rest.api.OrderCassandraApi
import com.qualityminds.lazyval.integration.boundary.rest.model.CreateOrder
import com.qualityminds.lazyval.integration.boundary.rest.model.Order
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import io.smallrye.common.annotation.Identifier
import jakarta.inject.Inject
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import java.util.UUID

@Produces(MediaType.APPLICATION_JSON)
class OrderCassandraResource @Inject constructor(
    private val mapper: RestMapper,
    @Identifier("cassandra") private val repository: OrderRepository
) : OrderCassandraApi {

    override fun createOrderCassandra(createOrder: CreateOrder): Order {
        val newOrder = com.qualityminds.lazyval.integration.domain.Order.create(
            createOrder.isbn, createOrder.quantity, createOrder.email
        )
        repository.save(newOrder)
        return mapper.toDto(newOrder)
    }

    override fun findOrdersByIsbnCassandra(isbn: String): List<Order> =
        mapper.toDto(repository.findByIsbn(Isbn.parse(isbn)))

    override fun getAllOrdersCassandra(): List<Order> = mapper.toDto(repository.findAll())

    override fun getOrderByIdCassandra(id: UUID): Order = mapper.toDto(repository.getById(id))
}
