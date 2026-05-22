package com.qualityminds.lazyval.integration.boundary.rest

import com.qualityminds.lazyval.integration.boundary.rest.api.OrderJpaApi
import com.qualityminds.lazyval.integration.boundary.rest.model.CreateOrder
import com.qualityminds.lazyval.integration.boundary.rest.model.Order
import com.qualityminds.lazyval.integration.domain.EMail
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import io.smallrye.common.annotation.Identifier
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import java.util.UUID

@Produces(MediaType.APPLICATION_JSON)
class OrderJpaResource @Inject constructor(
    private val mapper: RestMapper,
    @Identifier("jpa") private val repository: OrderRepository
) : OrderJpaApi {

    @Transactional
    override fun createOrderJpa(createOrder: CreateOrder): Order {
        val newOrder = com.qualityminds.lazyval.integration.domain.Order.create(
            Isbn.parse(createOrder.isbn),
            Quantity(createOrder.quantity),
            EMail(createOrder.email),
        )
        repository.save(newOrder)
        return mapper.toDto(newOrder)
    }

    override fun findOrdersByIsbnJpa(isbn: String): List<Order> =
        mapper.toDto(repository.findByIsbn(Isbn.parse(isbn)))

    override fun getAllOrdersJpa(): List<Order> = mapper.toDto(repository.findAll())

    override fun getOrderByIdJpa(id: UUID): Order = mapper.toDto(repository.getById(id))
}
