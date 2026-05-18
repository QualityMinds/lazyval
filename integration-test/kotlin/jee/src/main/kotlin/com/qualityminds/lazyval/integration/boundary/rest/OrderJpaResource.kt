package com.qualityminds.lazyval.integration.boundary.rest

import com.qualityminds.lazyval.integration.boundary.rest.api.OrderJpaApi
import com.qualityminds.lazyval.integration.boundary.rest.model.CreateOrder
import com.qualityminds.lazyval.integration.boundary.rest.model.Order
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.transaction.Transactional
import jakarta.ws.rs.Path
import java.util.UUID

// @Path is declared on OrderJpaApi (the openapi-generated interface), but Liberty's JAX-RS
// implementation only treats classes annotated directly with @Path as root resources.
@Path("/order/jpa")
@RequestScoped
class OrderJpaResource @Inject constructor(
    private val mapper: RestMapper,
    @Named("jpa") private val repository: OrderRepository
) : OrderJpaApi {

    @Transactional
    override fun createOrderJpa(createOrder: CreateOrder): Order {
        val newOrder = com.qualityminds.lazyval.integration.domain.Order.create(
            createOrder.isbn, createOrder.quantity, createOrder.email
        )
        repository.save(newOrder)
        return mapper.toDto(newOrder)
    }

    override fun findOrdersByIsbnJpa(isbn: String): List<Order> =
        mapper.toDto(repository.findByIsbn(Isbn.parse(isbn)))

    override fun getAllOrdersJpa(): List<Order> = mapper.toDto(repository.findAll())

    override fun getOrderByIdJpa(id: UUID): Order = mapper.toDto(repository.getById(id))
}
