package de.qualityminds.lazyval.integration

import de.qualityminds.lazyval.integration.shared.Isbn
import de.qualityminds.lazyval.integration.shared.Quantity
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class Startup(private val orderRepository: OrderRepository) {

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun onStart() {
        val orderA = Order(
            isbn = Isbn.parse("3-86680-192-0"),
            quantity = Quantity(1),
            email = EMail("a@b.de")
        )
        val orderB = Order(
            isbn = Isbn.parse("978-3-86680-192-9"),
            quantity = Quantity(1),
            email = EMail("x@y.de")
        )

        orderRepository.save(orderA)
        orderRepository.save(orderB)
    }
}