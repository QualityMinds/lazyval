package com.qualityminds.lazyval.integration.application;

import com.qualityminds.lazyval.integration.domain.Order;
import com.qualityminds.lazyval.integration.domain.OrderRepository;
import com.qualityminds.lazyval.integration.shared.Isbn;
import io.quarkus.cache.CacheResult;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A lookup cached in Redis, keyed by a domain-primitive. Counts its invocations so a test can tell a
 * cache hit from a miss.
 */
@ApplicationScoped
public class CachedOrderLookup {

    public static final String CACHE_NAME = "orderByIsbn";

    private final OrderRepository repository;
    private final AtomicInteger invocations = new AtomicInteger();

    @Inject
    CachedOrderLookup(@Identifier("jpa") OrderRepository repository) {
        this.repository = repository;
    }

    @CacheResult(cacheName = CACHE_NAME)
    public Order firstByIsbn(Isbn isbn) {
        invocations.incrementAndGet();
        return repository.findByIsbn(isbn).get(0);
    }

    public int invocations() {
        return invocations.get();
    }
}
