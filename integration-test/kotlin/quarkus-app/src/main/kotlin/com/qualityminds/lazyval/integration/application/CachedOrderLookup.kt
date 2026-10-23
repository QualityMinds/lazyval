package com.qualityminds.lazyval.integration.application

import com.qualityminds.lazyval.integration.domain.Order
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import io.quarkus.cache.CacheResult
import io.smallrye.common.annotation.Identifier
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.concurrent.atomic.AtomicInteger

/**
 * A lookup cached in Redis, keyed by a domain-primitive. Counts its invocations so a test can tell a
 * cache hit from a miss.
 */
@ApplicationScoped
class CachedOrderLookup @Inject constructor(@Identifier("jpa") private val repository: OrderRepository) {

    private val invocations = AtomicInteger()

    @CacheResult(cacheName = CACHE_NAME)
    fun firstByIsbn(isbn: Isbn): Order {
        invocations.incrementAndGet()
        return repository.findByIsbn(isbn).first()
    }

    fun invocations(): Int = invocations.get()

    companion object {
        const val CACHE_NAME = "orderByIsbn"
    }
}
