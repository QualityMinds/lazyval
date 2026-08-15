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
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * One controller for every persistence technology under test: the `persistenceType` path segment
 * picks the repository. Keeps the API contract identical across backends instead of duplicating
 * the resource per store.
 */
@RestController
@RequestMapping("/order/{persistenceType}", produces = [MediaType.APPLICATION_JSON_VALUE])
class OrderController(
    private val mapper: RestMapper,
    @Qualifier("jpa") private val jpaRepository: OrderRepository,
    @Qualifier("cassandra") private val cassandraRepository: OrderRepository,
    @Qualifier("mongo") private val mongoRepository: OrderRepository,
    @Qualifier("jdbc") private val jdbcRepository: OrderRepository,
) : OrderApi {

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    override fun createOrder(
        @PathVariable persistenceType: PersistenceType,
        @RequestBody createOrder: CreateOrder,
    ): ResponseEntity<Order> {
        val newOrder = com.qualityminds.lazyval.integration.domain.Order.create(
            Isbn.parse(createOrder.isbn),
            Quantity(createOrder.quantity),
            EMail(createOrder.email),
            CouponCode.ofNullable(createOrder.couponCode),
        )
        repository(persistenceType).save(newOrder)
        return ResponseEntity.ok(mapper.toDto(newOrder))
    }

    @GetMapping("/isbn/{isbn}")
    override fun findOrdersByIsbn(
        @PathVariable persistenceType: PersistenceType,
        @PathVariable isbn: String,
    ): ResponseEntity<List<Order>> =
        ResponseEntity.ok(mapper.toDto(repository(persistenceType).findByIsbn(Isbn.parse(isbn))))

    @GetMapping
    override fun getAllOrders(@PathVariable persistenceType: PersistenceType): ResponseEntity<List<Order>> =
        ResponseEntity.ok(mapper.toDto(repository(persistenceType).findAll()))

    @GetMapping("/{id}")
    override fun getOrderById(
        @PathVariable persistenceType: PersistenceType,
        @PathVariable id: UUID,
    ): ResponseEntity<Order> =
        ResponseEntity.ok(mapper.toDto(repository(persistenceType).getById(id)))

    private fun repository(persistenceType: PersistenceType): OrderRepository =
        when (persistenceType) {
            PersistenceType.JPA -> jpaRepository
            PersistenceType.CASSANDRA -> cassandraRepository
            PersistenceType.MONGO -> mongoRepository
            PersistenceType.JDBC -> jdbcRepository
            // R2DBC lives in the spring-app-r2dbc scenario and answers with 501 here.
            else -> throw UnsupportedPersistenceTypeException(persistenceType)
        }
}
