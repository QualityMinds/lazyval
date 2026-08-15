package com.qualityminds.lazyval.integration.boundary.rest

import com.qualityminds.lazyval.integration.boundary.rest.api.OrderApi
import com.qualityminds.lazyval.integration.boundary.rest.model.CreateOrder
import com.qualityminds.lazyval.integration.boundary.rest.model.Order
import com.qualityminds.lazyval.integration.boundary.rest.model.PersistenceType
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.CouponCode
import com.qualityminds.lazyval.integration.shared.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import io.smallrye.common.annotation.Identifier
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import java.util.UUID

/**
 * One resource for every persistence technology under test: the `persistenceType` path segment
 * picks the repository. Keeps the API contract identical across backends instead of duplicating
 * the resource per store.
 */
@Produces(MediaType.APPLICATION_JSON)
class OrderResource @Inject constructor(
    private val mapper: RestMapper,
    @Identifier("jpa") private val jpaRepository: OrderRepository,
    @Identifier("cassandra") private val cassandraRepository: OrderRepository,
    @Identifier("mongo") private val mongoRepository: OrderRepository,
) : OrderApi {

    // Required by the JPA backend; a JTA transaction around a Cassandra or Mongo write is a no-op.
    @Transactional
    override fun createOrder(persistenceType: PersistenceType, createOrder: CreateOrder): Order {
        val newOrder = com.qualityminds.lazyval.integration.domain.Order.create(
            Isbn.parse(createOrder.isbn),
            Quantity(createOrder.quantity),
            EMail(createOrder.email),
            CouponCode.ofNullable(createOrder.couponCode),
        )
        repository(persistenceType).save(newOrder)
        return mapper.toDto(newOrder)
    }

    override fun findOrdersByIsbn(persistenceType: PersistenceType, isbn: String): List<Order> =
        mapper.toDto(repository(persistenceType).findByIsbn(Isbn.parse(isbn)))

    override fun getAllOrders(persistenceType: PersistenceType): List<Order> =
        mapper.toDto(repository(persistenceType).findAll())

    override fun getOrderById(persistenceType: PersistenceType, id: UUID): Order =
        mapper.toDto(repository(persistenceType).getById(id))

    private fun repository(persistenceType: PersistenceType): OrderRepository =
        when (persistenceType) {
            PersistenceType.JPA -> jpaRepository
            PersistenceType.CASSANDRA -> cassandraRepository
            PersistenceType.MONGO -> mongoRepository
            // JDBC and R2DBC are Spring-only scenarios and answer with 501 here.
            else -> throw UnsupportedPersistenceTypeException(persistenceType)
        }
}
