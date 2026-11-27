package com.qualityminds.lazyval.integration.evaluation

import com.qualityminds.lazyval.integration.LazyvalJacksonModule
import com.qualityminds.lazyval.integration.domain.OrderDate
import com.qualityminds.lazyval.integration.shared.CouponCode
import com.qualityminds.lazyval.integration.shared.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Title
import spock.lang.Unroll
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper

import java.time.LocalDate

/**
 * AI evaluation finding K7 (GitHub issue "Evaluation: ksp", label evaluation). This specification intentionally
 * fails on main: it documents the expected behaviour, not the current one.
 * <p>
 * The generated Jackson module registers value (de)serializers only, no key (de)serializers. A domain-primitive used
 * as a map key is therefore written through {@code toString()} ({@code "ISBN{978-3-16-148410-0}"}), and read
 * through whatever Jackson finds on the class itself: a String constructor, private or not, which skips the factory
 * that validates and normalizes ({@code Isbn.parse}, {@code CouponCode.ofNullable}), or nothing at all for a
 * non-String payload ("Cannot find a (Map) Key deserializer").
 * <p>
 * No application context: a {@link JsonMapper} with the modules found on the classpath, as Spring Boot builds it.
 */
@Title("Evaluation K7 - domain-primitives as Jackson map keys")
class K7JacksonMapKeySpec extends Specification {

    @Shared
    JsonMapper mapper = JsonMapper.builder().findAndAddModules().build()

    def setupSpec() {
        assert mapper.registeredModules()*.getClass().contains(LazyvalJacksonModule)
    }

    @Unroll("K7 a map key of type #type.simpleName is written as its payload: #json")
    void "K7 a domain-primitive map key is written as its payload"() {
        expect:
        mapper.writeValueAsString([(key): 1]) == json

        where:
        type       | key                                     || json
        Isbn       | Isbn.parse("978-3-16-148410-0")         || '{"978-3-16-148410-0":1}'
        EMail      | new EMail("customer@example.com")       || '{"customer@example.com":1}'
        CouponCode | CouponCode.ofNullable("SUMMER25")       || '{"SUMMER25":1}'
        Quantity   | Quantity.of(2)                          || '{"2":1}'
        OrderDate  | new OrderDate(LocalDate.of(2026, 1, 1)) || '{"2026-01-01":1}'
    }

    @Unroll("K7 a map key of type #type.simpleName is read through its factory: #json")
    void "K7 a domain-primitive map key is read through its factory"() {
        when:
        Map<?, Integer> read = mapper.readValue(json, typeReference)

        then:
        read.keySet() == [key] as Set

        where:
        type       | json                 | typeReference                                    || key
        // the factory normalizes, the private constructor Jackson falls back to does not
        CouponCode | '{"  summer25  ":1}' | new TypeReference<Map<CouponCode, Integer>>() {} || CouponCode.ofNullable("  summer25  ")
        // no String constructor to fall back to
        Quantity   | '{"2":1}'            | new TypeReference<Map<Quantity, Integer>>() {}   || Quantity.of(2)
        OrderDate  | '{"2026-01-01":1}'   | new TypeReference<Map<OrderDate, Integer>>() {}  || new OrderDate(LocalDate.of(2026, 1, 1))
    }

    void "K7 a map key the factory rejects is not read into a domain-primitive"() {
        when: 'a key Isbn.parse rejects'
        Map<Isbn, Integer> read = mapper.readValue('{"<script>alert(1)</script>":1}',
                new TypeReference<Map<Isbn, Integer>>() {})

        then:
        thrown(Exception)
    }
}
