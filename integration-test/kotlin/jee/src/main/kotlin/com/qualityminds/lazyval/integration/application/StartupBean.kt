package com.qualityminds.lazyval.integration.application

import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.domain.Order
import com.qualityminds.lazyval.integration.domain.OrderDate
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import jakarta.annotation.PostConstruct
import jakarta.ejb.Singleton
import jakarta.ejb.Startup
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.util.UUID

@Singleton
@Startup
class StartupBean @Inject constructor(
    @Named("jpa") private val jpaRepository: OrderRepository,
    @Named("cassandra") private val cassandraRepository: OrderRepository
) {

    @Transactional
    @PostConstruct
    fun init() {
        if (jpaRepository.findAll().isEmpty()) {
            jpaRepository.save(DefaultOrderA)
            jpaRepository.save(DefaultOrderB)
            logger.info("Initialized JPA database with demo entities")
        }
        if (cassandraRepository.findAll().isEmpty()) {
            cassandraRepository.save(DefaultOrderA)
            cassandraRepository.save(DefaultOrderB)
            logger.info("Initialized Cassandra database with demo entities")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StartupBean::class.java)

        // Deterministic UUIDs so the in-container server and the test JVM agree on the seed
        // orders' identity; UUID.randomUUID() would diverge across JVMs.
        @JvmField
        val DefaultOrderA: Order = Order(
            UUID.fromString("a1a1a1a1-b2b2-c3c3-d4d4-e5e5e5e5e5e5"),
            Isbn.parse("3-86680-192-0"),
            Quantity(1),
            EMail("a@b.de"),
            OrderDate.now()
        )

        @JvmField
        val DefaultOrderB: Order = Order(
            UUID.fromString("f6f6f6f6-a7a7-b8b8-c9c9-d0d0d0d0d0d0"),
            Isbn.parse("978-3-86680-192-9"),
            Quantity(1),
            EMail("x@y.de"),
            OrderDate.now()
        )
    }
}
