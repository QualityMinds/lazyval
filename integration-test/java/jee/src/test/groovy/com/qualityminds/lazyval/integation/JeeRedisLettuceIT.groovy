package com.qualityminds.lazyval.integation

import com.qualityminds.lazyval.integration.boundary.persistence.redis.LazyvalLettuceCodecs
import com.qualityminds.lazyval.integration.application.StartupBean
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import spock.lang.Shared
import spock.lang.Title

/**
 * The generated Lettuce codecs used the way an application does: composed per connection with
 * {@link RedisCodec#of}, here with plain {@code String} keys. Runs against the same Redis the Liberty
 * application writes to, reached through the host-mapped port.
 */
@Title("Java - JakartaEE Redis: Lettuce codecs")
class JeeRedisLettuceIT extends AbstractLibertyIT {

    @Shared
    RedisClient client = RedisClient.create(redisContainer.redisURI)
    @Shared
    List<StatefulRedisConnection> connections = []

    def cleanup() {
        def keys = raw().keys('template:*') + raw().keys('978-*')
        if (keys) {
            raw().del(keys as String[])
        }
    }

    def cleanupSpec() {
        connections*.close()
        client.shutdown()
    }

    def "a domain-primitive round-trips as its plain payload"() {
        given:
        def commands = valuesOf(LazyvalLettuceCodecs.ISBN)
        def isbn = Isbn.parse('978-3-16-148410-0')

        when:
        commands.set('template:isbn', isbn)

        then: 'read back as domain-primitive'
        commands.get('template:isbn') == isbn

        and: 'stored as the wrapped value, readable without Lazyval'
        raw().get('template:isbn') == '978-3-16-148410-0'
    }

    def "a numeric payload stays usable for Redis' own commands"() {
        given:
        def commands = valuesOf(LazyvalLettuceCodecs.QUANTITY)
        commands.set('template:quantity', new Quantity(2))

        when: 'Redis increments the stored value natively'
        raw().incr('template:quantity')

        then:
        commands.get('template:quantity') == new Quantity(3)
    }

    def "domain-primitives work in Redis data structures"() {
        given:
        def commands = valuesOf(LazyvalLettuceCodecs.ISBN)
        def a = Isbn.parse('978-3-16-148410-0')
        def b = Isbn.parse('3-86680-192-0')

        when:
        commands.sadd('template:isbns', a, b)
        commands.hset('template:book', 'isbn', a)

        then:
        commands.smembers('template:isbns') == [a, b] as Set
        commands.hget('template:book', 'isbn') == a
    }

    def "a domain-primitive as key is its plain payload"() {
        given:
        def isbn = Isbn.parse('978-3-16-148410-0')
        def commands = connect(RedisCodec.of(LazyvalLettuceCodecs.ISBN, StringCodec.UTF8))

        when:
        commands.set(isbn, 'some value')

        then:
        commands.get(isbn) == 'some value'
        raw().exists('978-3-16-148410-0') == 1L
    }

    def "the stored order is a hash of plain payloads"() {
        given: 'the hash StartupBean saved through the Redis repository'
        def order = StartupBean.DefaultOrderB

        when:
        def hash = raw().hgetall("orders:${order.id()}".toString())

        then:
        hash.isbn == order.isbn().value()
        hash.quantity == order.quantity().value().toString()
        hash.email == order.email().value()
        hash.currency == order.currency().currencyCode
        hash.orderDate == order.orderDate().date().toString()
        hash.couponCode == order.couponCode().value()
    }

    private <V> RedisCommands<String, V> valuesOf(RedisCodec<V, V> codec) {
        connect(RedisCodec.of(StringCodec.UTF8, codec))
    }

    private RedisCommands<String, String> raw() {
        connect(StringCodec.UTF8)
    }

    private <K, V> RedisCommands<K, V> connect(RedisCodec<K, V> codec) {
        def connection = client.connect(codec)
        connections << connection
        connection.sync()
    }
}
