package com.qualityminds.lazyval.integration.application

import com.qualityminds.lazyval.integration.domain.Order
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicInteger

/**
 * A lookup cached in Redis, keyed by a domain-primitive. Counts its invocations so a test can tell a
 * cache hit from a miss.
 */
@Service
class CachedOrderLookup(@Qualifier("jpa") private val repository: OrderRepository) {

    companion object {
        const val CACHE_NAME = "orderByIsbn"
    }

    private val invocations = AtomicInteger()

    @Cacheable(cacheNames = [CACHE_NAME])
    fun firstByIsbn(isbn: Isbn): Order {
        invocations.incrementAndGet()
        return repository.findByIsbn(isbn).first()
    }

    fun invocations(): Int = invocations.get()
}
