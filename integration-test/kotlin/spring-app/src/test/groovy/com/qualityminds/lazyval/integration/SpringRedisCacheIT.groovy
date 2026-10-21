package com.qualityminds.lazyval.integration

import com.qualityminds.lazyval.integration.application.CachedOrderLookup
import com.qualityminds.lazyval.integration.application.Startup
import com.qualityminds.lazyval.integration.shared.Quantity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
import org.springframework.data.redis.core.StringRedisTemplate
import spock.lang.Title
import tools.jackson.databind.ObjectMapper

@Title("Kotlin - Spring Cache backed by Redis")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpringRedisCacheIT extends AbstractIT {

    @Autowired
    CachedOrderLookup lookup
    @Autowired
    CacheManager cacheManager
    @Autowired
    StringRedisTemplate stringTemplate
    @Autowired
    ObjectMapper jsonMapper

    def setup() {
        // not clear(): with Lettuce the Redis cache clears asynchronously and may delete what a test just cached
        cacheManager.getCache(CachedOrderLookup.CACHE_NAME).invalidate()
    }

    def "a domain-primitive key hits the cache on the second call"() {
        given:
        def order = Startup.DefaultOrderB
        def before = lookup.invocations()

        when:
        def first = lookup.firstByIsbn(order.isbn)
        def second = lookup.firstByIsbn(order.isbn)

        then: 'the lookup ran once, the second call came from Redis'
        lookup.invocations() == before + 1

        and: 'the cached value deserializes back to an equal order'
        first == order
        second == order
    }

    def "the cache key is the wrapped value, not toString()"() {
        given:
        def order = Startup.DefaultOrderB

        when:
        lookup.firstByIsbn(order.isbn)

        then:
        stringTemplate.keys("${CachedOrderLookup.CACHE_NAME}::*".toString()) ==
                ["${CachedOrderLookup.CACHE_NAME}::${order.isbn.value}".toString()] as Set
    }

    def "the cached value is JSON with domain-primitives as scalars"() {
        given:
        def order = Startup.DefaultOrderB

        when:
        lookup.firstByIsbn(order.isbn)
        def json = stringTemplate.opsForValue().get("${CachedOrderLookup.CACHE_NAME}::${order.isbn.value}".toString())

        then: 'the Lazyval Jackson module writes the wrapped values'
        with(jsonMapper.readValue(json, Map)) {
            isbn == order.isbn.value
            Quantity.of(quantity) == order.quantity
            email == order.email.value
        }
    }
}
