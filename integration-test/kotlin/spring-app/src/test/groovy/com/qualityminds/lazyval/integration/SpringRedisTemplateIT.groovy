package com.qualityminds.lazyval.integration

import com.qualityminds.lazyval.integration.application.Startup
import com.qualityminds.lazyval.integration.boundary.persistence.LazyvalSpringDataConfiguration.RedisSerializers
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializer
import spock.lang.Title

@Title("Kotlin - Spring-Data Redis: RedisTemplate")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpringRedisTemplateIT extends AbstractIT {

    @Autowired
    RedisConnectionFactory connectionFactory
    @Autowired
    StringRedisTemplate stringTemplate

    def cleanup() {
        stringTemplate.delete(stringTemplate.keys('template:*'))
    }

    def "a domain-primitive round-trips as its plain payload"() {
        given:
        def template = templateFor(RedisSerializers.ISBN)
        def isbn = Isbn.parse('978-3-16-148410-0')

        when:
        template.opsForValue().set('template:isbn', isbn)

        then: 'read back as domain-primitive'
        template.opsForValue().get('template:isbn') == isbn

        and: 'stored as the wrapped value, readable without Lazyval'
        stringTemplate.opsForValue().get('template:isbn') == '978-3-16-148410-0'
    }

    def "a numeric payload stays usable for Redis' own commands"() {
        given:
        def template = templateFor(RedisSerializers.QUANTITY)
        template.opsForValue().set('template:quantity', Quantity.of(2))

        when: 'Redis increments the stored value natively'
        stringTemplate.opsForValue().increment('template:quantity')

        then:
        template.opsForValue().get('template:quantity') == Quantity.of(3)
    }

    def "domain-primitives work in Redis data structures"() {
        given:
        def template = templateFor(RedisSerializers.ISBN)
        def a = Isbn.parse('978-3-16-148410-0')
        def b = Isbn.parse('3-86680-192-0')

        when:
        template.opsForSet().add('template:isbns', a, b)
        template.opsForHash().put('template:book', 'isbn', a)

        then:
        template.opsForSet().members('template:isbns') == [a, b] as Set
        template.opsForHash().get('template:book', 'isbn') == a
    }

    def "the template reads what the repository wrote"() {
        given: 'the hash Startup saved through the Redis repository'
        def template = templateFor(RedisSerializers.ISBN)
        def order = Startup.DefaultOrderB

        expect: 'serializers and repository converters share the same bytes'
        template.opsForHash().get("orders:${order.id}".toString(), 'isbn') == order.isbn
    }

    private <T> RedisTemplate<String, T> templateFor(RedisSerializer<T> serializer) {
        def template = new RedisTemplate<String, T>()
        template.connectionFactory = connectionFactory
        template.keySerializer = RedisSerializer.string()
        template.valueSerializer = serializer
        template.hashKeySerializer = RedisSerializer.string()
        template.hashValueSerializer = serializer
        template.afterPropertiesSet()
        template
    }
}
