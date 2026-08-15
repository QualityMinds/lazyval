package com.qualityminds.lazyval.integration.application

import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class Startup(
    @Qualifier("jpa") private val jpaRepository: OrderRepository,
    @Qualifier("cassandra") private val cassandraRepository: OrderRepository,
    @Qualifier("mongo") private val mongoRepository: OrderRepository,
    @Qualifier("jdbc") private val jdbcRepository: OrderRepository
) {

    companion object {
        val DefaultOrderA: com.qualityminds.lazyval.integration.domain.Order =
            com.qualityminds.lazyval.integration.domain.Order.create(
                Isbn.parse("3-86680-192-0"),
                Quantity(1),
                EMail("a@b.de")
            )
        val DefaultOrderB: com.qualityminds.lazyval.integration.domain.Order =
            com.qualityminds.lazyval.integration.domain.Order.create(
                Isbn.parse("978-3-86680-192-9"),
                Quantity(1),
                EMail("x@y.de")
            )
    }

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun onStart() {
        // push demo entities to all storages
        if (jpaRepository.findAll().isEmpty()) {
            jpaRepository.save(DefaultOrderA)
            jpaRepository.save(DefaultOrderB)
        }
        if (cassandraRepository.findAll().isEmpty()) {
            cassandraRepository.save(DefaultOrderA)
            cassandraRepository.save(DefaultOrderB)
        }
        if (mongoRepository.findAll().isEmpty()) {
            mongoRepository.save(DefaultOrderA)
            mongoRepository.save(DefaultOrderB)
        }
        if (jdbcRepository.findAll().isEmpty()) {
            jdbcRepository.save(DefaultOrderA)
            jdbcRepository.save(DefaultOrderB)
        }
    }
}
