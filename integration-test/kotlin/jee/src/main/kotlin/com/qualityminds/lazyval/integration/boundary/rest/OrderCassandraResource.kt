package com.qualityminds.lazyval.integration.boundary.rest

import com.qualityminds.lazyval.integration.boundary.rest.api.OrderCassandraApi
import com.qualityminds.lazyval.integration.boundary.rest.model.CreateOrder
import com.qualityminds.lazyval.integration.boundary.rest.model.Order
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.ws.rs.Path
import java.util.UUID

// @Path is declared on OrderCassandraApi (the openapi-generated interface), but Liberty's JAX-RS
// implementation only treats classes annotated directly with @Path as root resources.
@Path("/order/cassandra")
@RequestScoped
class OrderCassandraResource @Inject constructor(
    private val mapper: RestMapper,
    @Named("cassandra") private val repository: OrderRepository
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
