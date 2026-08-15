package com.qualityminds.lazyval.integration.boundary.persistence.r2dbc

import com.qualityminds.lazyval.integration.domain.Order
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

@Repository
@Qualifier("r2dbc")
class R2dbcOrderRepository(
    private val springDataRepository: SpringDataR2dbcRepository,
    private val mapper: R2dbcMapper,
) : OrderRepository {

    override fun save(order: Order): Mono<Order> =
        springDataRepository.save(mapper.toDB(order)).map(mapper::toDomain)

    // sorted by ISBN like the other scenarios, so the ITs can assert a stable order
    override fun findAll(): Flux<Order> =
        springDataRepository.findAll()
            .map(mapper::toDomain)
            .sort(Comparator.comparing { order -> order.isbn.value })

    override fun getById(id: UUID): Mono<Order> =
        springDataRepository.findById(id).map(mapper::toDomain)

    override fun findByIsbn(isbn: Isbn): Flux<Order> =
        springDataRepository.findByIsbn(isbn).map(mapper::toDomain)
}
