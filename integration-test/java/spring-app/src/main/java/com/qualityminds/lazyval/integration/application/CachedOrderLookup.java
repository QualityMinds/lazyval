package com.qualityminds.lazyval.integration.application;

import com.qualityminds.lazyval.integration.domain.Order;
import com.qualityminds.lazyval.integration.domain.OrderRepository;
import com.qualityminds.lazyval.integration.shared.Isbn;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A lookup cached in Redis, keyed by a domain-primitive. Counts its invocations so a test can tell a
 * cache hit from a miss.
 */
@Service
public class CachedOrderLookup {

    public static final String CACHE_NAME = "orderByIsbn";

    private final OrderRepository repository;
    private final AtomicInteger invocations = new AtomicInteger();

    CachedOrderLookup(@Qualifier("jpa") OrderRepository repository) {
        this.repository = repository;
    }

    @Cacheable(cacheNames = CACHE_NAME)
    public Order firstByIsbn(Isbn isbn) {
        invocations.incrementAndGet();
        return repository.findByIsbn(isbn).get(0);
    }

    public int invocations() {
        return invocations.get();
    }
}
