package com.qualityminds.lazyval.integration.domain

import com.qualityminds.lazyval.integration.shared.Isbn
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Reactive counterpart to the blocking port in the kotlin/spring-app scenario. Reactor types rather
 * than coroutines: R2DBC is reactive by construction, and this scenario deliberately keeps the
 * coroutine adapter out of the path so a startup failure points at Spring's dependency resolution
 * of the KSP-generated converter config rather than at the bridge.
 */
interface OrderRepository {

    fun save(order: Order): Mono<Order>

    fun findAll(): Flux<Order>

    fun getById(id: UUID): Mono<Order>

    fun findByIsbn(isbn: Isbn): Flux<Order>
}
