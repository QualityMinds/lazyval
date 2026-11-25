package com.qualityminds.lazyval.integration

import com.qualityminds.lazyval.integration.boundary.rest.model.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Title
import spock.lang.Unroll
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.exc.MismatchedInputException

/**
 * Reads the generated {@link Order} model through the application's own {@link ObjectMapper}, i.e. the
 * Spring Boot configuration with the {@code LazyvalJacksonModule} picked up via ServiceLoader.
 *
 * A domain-primitive is a scalar on the wire, so any other token has to be rejected the way Jackson rejects
 * it for the payload type itself. {@code orderDate} is the control case: its deserializer delegates to
 * Jackson's {@code LocalDate} deserializer instead of reading the token itself.
 */
@Title("Java - Spring Jackson")
@SpringBootTest
class SpringJacksonIT extends AbstractIT {

    // couponCode sits before currency and orderDate on purpose: a deserializer that does not consume
    // the token it was handed leaves the parser inside the nested value, which shows up in the fields after it
    static final Map<String, String> VALID_FIELDS = [
            id        : '"550e8400-e29b-41d4-a716-446655440000"',
            isbn      : '"978-3-16-148410-0"',
            quantity  : '2',
            email     : '"customer@example.com"',
            couponCode: '"SUMMER25"',
            currency  : '"EUR"',
            orderDate : '"2026-01-01"',
    ]

    @Autowired // this is already configured with the LazyvalModule
    ObjectMapper jsonMapper

    def "scalar payloads are read into domain-primitives"() {
        when:
        def result = read(orderJson([:]))

        then:
        result instanceof Order
        with(result as Order) {
            isbn.value() == '978-3-16-148410-0'
            quantity.value() == 2
            couponCode.value() == 'SUMMER25'
            currency == 'EUR'
        }
    }

    @Unroll("invalid input must not be parsed as default value but throw an exception")
    def "#field given #description is rejected as mismatched input"() {
        when:
        def result = read(orderJson([(field): json]))

        then:
        result instanceof MismatchedInputException
        (result as MismatchedInputException).path*.propertyName == [field]

        where:
        field        | json                            | description
        'quantity'   | '{"value":2}'                   | 'an object'
        'quantity'   | '[2]'                           | 'an array'
        'quantity'   | 'true'                          | 'a boolean'
        'quantity'   | '"two"'                         | 'a non-numeric string'
        'isbn'       | '{"value":"978-3-16-148410-0"}' | 'an object'
        'couponCode' | '{"value":"SUMMER25"}'          | 'an object'
        'orderDate'  | '{"value":"2026-01-01"}'        | 'an object'
    }

    /**
     * Returns either the read {@link Order} or the exception, so a failing condition prints what the
     * mapper actually produced instead of only "no exception thrown".
     */
    private Object read(String json) {
        try {
            return jsonMapper.readValue(json, Order)
        } catch (Exception e) {
            return e
        }
    }

    /*
     * Prepares the Order json-payload
     */
    private static String orderJson(Map<String, String> overrides) {
        def fields = VALID_FIELDS + overrides
        return '{' + fields.collect { name, value -> "\"$name\":$value" }.join(',') + '}'
    }
}
