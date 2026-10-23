package com.qualityminds.lazyval.integation

import com.qualityminds.lazyval.integration.RedisTestResource
import com.qualityminds.lazyval.integration.application.CachedOrderLookup
import com.qualityminds.lazyval.integration.application.Startup
import com.qualityminds.lazyval.integration.shared.Quantity
import io.quarkus.cache.Cache
import io.quarkus.cache.CacheName
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

/**
 * The Redis cache encodes keys and values with the same codec lookup as the data source, so without
 * a {@code Codec} bean a domain-primitive key is JSON as well: {@code cache:<name>:"<value>"}.
 */
@DisplayName("Kotlin - Quarkus Cache backed by Redis")
@QuarkusTest
@QuarkusTestResource(value = RedisTestResource.class)
class QuarkusRedisCacheIT {

    @Inject
    CachedOrderLookup lookup
    @Inject
    @CacheName(CachedOrderLookup.CACHE_NAME)
    Cache cache
    @Inject
    RedisDataSource dataSource

    @BeforeEach
    void invalidate() {
        cache.invalidateAll().await().indefinitely()
    }

    @Test
    @DisplayName("a domain-primitive key hits the cache on the second call")
    void cacheHit() {
        def order = Startup.DefaultOrderB
        def before = lookup.invocations()

        def first = lookup.firstByIsbn(order.isbn)
        def second = lookup.firstByIsbn(order.isbn)

        assert lookup.invocations() == before + 1
        assert first == order
        assert second == order
    }

    @Test
    @DisplayName("the cache key is the JSON-encoded wrapped value")
    void cacheKey() {
        def order = Startup.DefaultOrderB

        lookup.firstByIsbn(order.isbn)

        assert dataSource.key().keys("cache:${CachedOrderLookup.CACHE_NAME}:*".toString()) ==
                ["cache:${CachedOrderLookup.CACHE_NAME}:\"${order.isbn.value}\"".toString()]
    }

    @Test
    @DisplayName("the cached value is JSON with domain-primitives as scalars")
    void cachedValue() {
        def order = Startup.DefaultOrderB

        lookup.firstByIsbn(order.isbn)
        def json = dataSource.value(String).get("cache:${CachedOrderLookup.CACHE_NAME}:\"${order.isbn.value}\"".toString())

        Map stored = new ObjectMapper().readValue(json, Map)
        assert stored.isbn == order.isbn.value
        assert Quantity.of(stored.quantity) == order.quantity
        assert stored.email == order.email.value
    }
}
