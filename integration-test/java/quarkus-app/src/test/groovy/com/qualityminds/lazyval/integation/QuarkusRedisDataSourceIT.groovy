package com.qualityminds.lazyval.integation

import com.qualityminds.lazyval.integration.RedisTestResource
import com.qualityminds.lazyval.integration.application.Startup
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

/**
 * The Quarkus counterpart of Spring's RedisTemplate: typed command groups on the {@link RedisDataSource}.
 *
 * Lazyval generates no Redis codec for Quarkus. A domain-primitive therefore takes Quarkus' fallback
 * for types without a {@code Codec} bean: JSON through the application's {@code ObjectMapper}, which
 * carries the Lazyval Jackson module. Redis still stores bytes — they are just the UTF-8 bytes of that
 * JSON text, so a String payload keeps its quotes.
 */
@DisplayName("Java - Quarkus Redis data source")
@QuarkusTest
@QuarkusTestResource(value = RedisTestResource.class)
class QuarkusRedisDataSourceIT {

    @Inject
    RedisDataSource dataSource

    @AfterEach
    void cleanup() {
        def keys = dataSource.key().keys('template:*') + dataSource.key().keys('"*')
        if (!keys.isEmpty()) {
            dataSource.key().del(keys as String[])
        }
    }

    @Test
    @DisplayName("a domain-primitive round-trips as a JSON scalar")
    void valueRoundTrip() {
        def isbn = Isbn.parse('978-3-16-148410-0')

        dataSource.value(Isbn).set('template:isbn', isbn)

        assert dataSource.value(Isbn).get('template:isbn') == isbn
        // the stored bytes are JSON text: the wrapped value, including the quotes of a JSON string
        assert dataSource.value(String).get('template:isbn') == '"978-3-16-148410-0"'
    }

    @Test
    @DisplayName("a numeric payload stays usable for Redis' own commands")
    void numericPayload() {
        dataSource.value(Quantity).set('template:quantity', new Quantity(2))

        // a JSON number is the same text as Redis' own integer representation
        dataSource.value(String).incr('template:quantity')

        assert dataSource.value(Quantity).get('template:quantity') == new Quantity(3)
    }

    @Test
    @DisplayName("domain-primitives work in Redis data structures")
    void dataStructures() {
        def a = Isbn.parse('978-3-16-148410-0')
        def b = Isbn.parse('3-86680-192-0')

        dataSource.set(Isbn).sadd('template:isbns', a, b)
        dataSource.hash(Isbn).hset('template:book', 'isbn', a)

        assert dataSource.set(Isbn).smembers('template:isbns') == [a, b] as Set
        assert dataSource.hash(Isbn).hget('template:book', 'isbn') == a
    }

    @Test
    @DisplayName("a domain-primitive as key is encoded like a value")
    void domainPrimitiveAsKey() {
        def isbn = Isbn.parse('978-3-16-148410-0')

        dataSource.value(Isbn, String).set(isbn, 'some value')

        assert dataSource.value(Isbn, String).get(isbn) == 'some value'
        // keys go through the same codec lookup, so the Redis key carries the JSON quotes as well
        assert dataSource.key().exists('"978-3-16-148410-0"')
    }

    @Test
    @DisplayName("the stored order is JSON with domain-primitives as scalars")
    void readsWhatTheRepositoryWrote() {
        def order = Startup.DefaultOrderB

        def json = dataSource.value(String).get("orders:${order.id()}".toString())

        Map stored = new ObjectMapper().readValue(json, Map)
        assert stored.isbn == order.isbn().value()
        assert stored.quantity == order.quantity().value()
        assert stored.email == order.email().value()
        assert stored.currency == order.currency().currencyCode
    }
}
