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
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Kotlin controller implementing the Java-generated reactive interface — no coroutines. `OrderApi`
 * comes from the `spring` generator with reactive=true, so the signatures are Reactor types.
 *
 * This deployment implements [PersistenceType.R2DBC] only; every other type is a valid value in the
 * shared contract and answers 501.
 */
@RestController
@RequestMapping("/order/{persistenceType}", produces = [MediaType.APPLICATION_JSON_VALUE])
class OrderController(
    private val mapper: RestMapper,
    @Qualifier("r2dbc") private val r2dbcRepository: OrderRepository,
) : OrderApi {

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    override fun createOrder(
        @PathVariable persistenceType: PersistenceType,
        @RequestBody createOrder: Mono<CreateOrder>,
        exchange: ServerWebExchange,
    ): Mono<ResponseEntity<Order>> =
        repository(persistenceType)
            .flatMap { repository ->
                createOrder
                    .map { dto ->
                        com.qualityminds.lazyval.integration.domain.Order.create(
                            Isbn.parse(dto.isbn),
                            Quantity.of(dto.quantity),
                            EMail(dto.email),
                            CouponCode.ofNullable(dto.couponCode),
                        )
                    }
                    .flatMap(repository::save)
            }
            .map { saved -> ResponseEntity.ok(mapper.toDto(saved)) }

    @GetMapping("/isbn/{isbn}")
    override fun findOrdersByIsbn(
        @PathVariable persistenceType: PersistenceType,
        @PathVariable isbn: String,
        exchange: ServerWebExchange,
    ): Mono<ResponseEntity<Flux<Order>>> =
        repository(persistenceType)
            .map { repository ->
                ResponseEntity.ok(repository.findByIsbn(Isbn.parse(isbn)).map(mapper::toDto))
            }

    @GetMapping
    override fun getAllOrders(
        @PathVariable persistenceType: PersistenceType,
        exchange: ServerWebExchange,
    ): Mono<ResponseEntity<Flux<Order>>> =
        repository(persistenceType)
            .map { repository -> ResponseEntity.ok(repository.findAll().map(mapper::toDto)) }

    @GetMapping("/{id}")
    override fun getOrderById(
        @PathVariable persistenceType: PersistenceType,
        @PathVariable id: UUID,
        exchange: ServerWebExchange,
    ): Mono<ResponseEntity<Order>> =
        repository(persistenceType)
            .flatMap { repository -> repository.getById(id) }
            .map { order -> ResponseEntity.ok(mapper.toDto(order)) }
            .defaultIfEmpty(ResponseEntity.notFound().build())

    /**
     * Returned as a `Mono` rather than thrown directly so the 501 travels as an error signal on the
     * reactive chain.
     */
    private fun repository(persistenceType: PersistenceType): Mono<OrderRepository> =
        if (persistenceType == PersistenceType.R2DBC) {
            Mono.just(r2dbcRepository)
        } else {
            Mono.error(UnsupportedPersistenceTypeException(persistenceType))
        }
}
