package com.qualityminds.lazyval.integration.evaluation

import com.qualityminds.lazyval.integration.boundary.persistence.LazyvalSpringDataConfiguration.RedisSerializers
import spock.lang.Specification
import spock.lang.Title

/**
 * Documents AI evaluation finding P15 (GitHub issue "Evaluation: processor", label evaluation); the features
 * intentionally fail on main.
 * <p>
 * The generated Spring Data Redis serializers write {@code null} as {@code byte[0]}, like Spring's own serializers, but
 * read {@code byte[0]} back as a payload: {@code Isbn.parse("")} throws, {@code new EMail("")} silently becomes a
 * value, the {@code int} payload of {@code Quantity} unboxes {@code null}, and {@code LocalDate} fails to convert.
 * Whatever a serializer writes for {@code null} has to be read back as {@code null}. {@code COUPON_CODE} is not listed:
 * it only round-trips because {@code CouponCode.ofNullable} maps a blank payload to {@code null}.
 * <p>
 * Uses the {@code RedisSerializers} generated into this module's {@code LazyvalSpringDataConfiguration}, without a
 * Spring context or a Redis server.
 */
@Title("Evaluation P15 - Spring Data Redis serializers and null")
class RedisSerializerNullEvaluationSpec extends Specification {

    def "P15 RedisSerializers.#name reads what it writes for null back as null"() {
        given:
        def written = serializer.serialize(null)

        when:
        def read = serializer.deserialize(written)

        then:
        noExceptionThrown()
        read == null

        where:
        name         | serializer
        'ISBN'       | RedisSerializers.ISBN
        'E_MAIL'     | RedisSerializers.E_MAIL
        'QUANTITY'   | RedisSerializers.QUANTITY
        'ORDER_DATE' | RedisSerializers.ORDER_DATE
    }
}
