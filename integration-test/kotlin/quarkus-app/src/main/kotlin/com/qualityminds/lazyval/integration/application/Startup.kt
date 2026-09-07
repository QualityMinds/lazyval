package com.qualityminds.lazyval.integration.application

import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.domain.Order
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.CouponCode
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import io.quarkus.runtime.StartupEvent
import io.smallrye.common.annotation.Identifier
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import jakarta.transaction.Transactional

@ApplicationScoped
class Startup @Inject constructor(
    @Identifier("jpa") private val jpaRepository: OrderRepository,
    @Identifier("cassandra") private val cassandraRepository: OrderRepository,
    @Identifier("mongo") private val mongoRepository: OrderRepository
) {

    @Transactional
    fun onStart(@Observes ev: StartupEvent) {
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
    }

    companion object {
        @JvmField
        val DefaultOrderA: Order = Order.create(
            Isbn.parse("3-86680-192-0"),
            Quantity.of(1),
            EMail("a@b.de")
        )

        @JvmField
        val DefaultOrderB: Order = Order.create(
            Isbn.parse("978-3-86680-192-9"),
            Quantity.of(1),
            EMail("x@y.de"),
            CouponCode.ofNullable("FRESH12")
        )
    }
}
