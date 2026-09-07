package com.qualityminds.lazyval.integration.application

import com.qualityminds.lazyval.integration.domain.Order
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

@Component
class Startup(@Qualifier("r2dbc") private val repository: OrderRepository) {

    /**
     * Blocks deliberately: seeding has to finish before the first request arrives, and
     * ApplicationReadyEvent is not on an event-loop thread. Everything the ITs exercise stays
     * non-blocking. Inserted with concatMap so the two rows land in a deterministic order.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun onStart() {
        repository.findAll()
            .hasElements()
            .filter { seeded -> !seeded }
            .flatMapMany { Flux.just(DefaultOrderA, DefaultOrderB) }
            .concatMap(repository::save)
            .then()
            .block()
    }

    companion object {
        @JvmField
        val DefaultOrderA: Order = Order.create(
            Isbn.parse("3-86680-192-0"),
            Quantity.of(1),
            EMail("a@b.de"),
        )

        @JvmField
        val DefaultOrderB: Order = Order.create(
            Isbn.parse("978-3-86680-192-9"),
            Quantity.of(1),
            EMail("x@y.de"),
        )
    }
}
