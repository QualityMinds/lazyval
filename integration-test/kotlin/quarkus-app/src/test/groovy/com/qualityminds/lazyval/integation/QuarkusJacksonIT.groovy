package com.qualityminds.lazyval.integation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.qualityminds.lazyval.integration.boundary.rest.model.Order
import com.qualityminds.lazyval.integration.shared.Quantity
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Reads the generated {@link Order} model through the application's own {@link ObjectMapper}, i.e. the
 * Quarkus configuration with the {@code LazyvalJackson2Module} registered as {@code ObjectMapperCustomizer}.
 *
 * A domain-primitive is a scalar on the wire, so any other token has to be rejected the way Jackson rejects
 * it for the payload type itself. {@code orderDate} is the control case: its deserializer delegates to
 * Jackson's {@code LocalDate} deserializer instead of reading the token itself.
 */
@DisplayName("Kotlin - Quarkus Jackson")
@QuarkusTest
class QuarkusJacksonIT {

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

    @Inject // this is already configured with the LazyvalModule
    ObjectMapper jsonMapper

    @Test
    void "scalar payloads are read into domain-primitives"() {
        def result = read(orderJson([:]))

        assert result instanceof Order
        def order = result as Order
        assert order.isbn.value == '978-3-16-148410-0'
        // the payload is the value class Amount, whose getter is mangled - compare the whole value instead
        assert order.quantity == Quantity.of(2)
        assert order.couponCode.value == 'SUMMER25'
        assert order.currency == 'EUR'
    }

    @Test
    void "quantity given an object is rejected as mismatched input"() {
        def result = read(orderJson(quantity: '{"value":2}'))

        assert result instanceof MismatchedInputException
        assert (result as MismatchedInputException).path*.fieldName == ['quantity']
    }

    @Test
    void "quantity given an array is rejected as mismatched input"() {
        def result = read(orderJson(quantity: '[2]'))

        assert result instanceof MismatchedInputException
        assert (result as MismatchedInputException).path*.fieldName == ['quantity']
    }

    @Test
    void "quantity given a boolean is rejected as mismatched input"() {
        def result = read(orderJson(quantity: 'true'))

        assert result instanceof MismatchedInputException
        assert (result as MismatchedInputException).path*.fieldName == ['quantity']
    }

    @Test
    void "quantity given a non-numeric string is rejected as mismatched input"() {
        def result = read(orderJson(quantity: '"two"'))

        assert result instanceof MismatchedInputException
        assert (result as MismatchedInputException).path*.fieldName == ['quantity']
    }

    @Test
    void "isbn given an object is rejected as mismatched input"() {
        def result = read(orderJson(isbn: '{"value":"978-3-16-148410-0"}'))

        assert result instanceof MismatchedInputException
        assert (result as MismatchedInputException).path*.fieldName == ['isbn']
    }

    @Test
    void "couponCode given an object is rejected as mismatched input"() {
        def result = read(orderJson(couponCode: '{"value":"SUMMER25"}'))

        assert result instanceof MismatchedInputException
        assert (result as MismatchedInputException).path*.fieldName == ['couponCode']
    }

    @Test
    void "couponCode given an object as last property is rejected as mismatched input"() {
        // as last property the unconsumed nested value is never noticed: Jackson 2 does not check for trailing tokens
        // by default, and no required property comes after it that could go missing
        def result = read('''{"id":"550e8400-e29b-41d4-a716-446655440000","isbn":"978-3-16-148410-0","quantity":2,\
"email":"customer@example.com","currency":"EUR","orderDate":"2026-01-01","couponCode":{"value":"SUMMER25"}}''')

        assert result instanceof MismatchedInputException
        assert (result as MismatchedInputException).path*.fieldName == ['couponCode']
    }

    @Test
    void "orderDate given an object is rejected as mismatched input"() {
        def result = read(orderJson(orderDate: '{"value":"2026-01-01"}'))

        assert result instanceof MismatchedInputException
        assert (result as MismatchedInputException).path*.fieldName == ['orderDate']
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
