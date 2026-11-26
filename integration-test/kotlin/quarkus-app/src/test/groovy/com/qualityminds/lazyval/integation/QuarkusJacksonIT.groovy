package com.qualityminds.lazyval.integation

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.qualityminds.lazyval.integration.LazyvalJackson2Module
import com.qualityminds.lazyval.integration.boundary.rest.model.Order
import com.qualityminds.lazyval.integration.domain.OrderDate
import com.qualityminds.lazyval.integration.shared.CouponCode
import com.qualityminds.lazyval.integration.shared.Quantity
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

import java.time.LocalDate

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

    /*
     * The next two tests build mappers of their own: each resolves the deserializer a domain-primitive
     * delegates to against its own configuration, so what one mapper resolved must not be what another
     * reads with. Both mappers are checked, since either one may be the first to resolve, or neither if
     * the application's mapper already did in an earlier test.
     */

    @Test
    void "a LocalDate format configured on one mapper is not applied by another"() {
        // a mapper reading LocalDate as dd.MM.yyyy, and one with the ISO default
        def dottedDates = JsonMapper.builder()
                .addModules(new JavaTimeModule(), new LazyvalJackson2Module())
                .withConfigOverride(LocalDate, { it.setFormat(JsonFormat.Value.forPattern('dd.MM.yyyy')) })
                .build()
        def isoDates = JsonMapper.builder().addModules(new JavaTimeModule(), new LazyvalJackson2Module()).build()

        assert read(dottedDates, '"01.01.2026"', OrderDate) == new OrderDate(LocalDate.of(2026, 1, 1))
        assert read(isoDates, '"2026-01-01"', OrderDate) == new OrderDate(LocalDate.of(2026, 1, 1))
    }

    @Test
    void "an int deserializer registered on one mapper is not applied by another"() {
        // a mapper whose own int deserializer rejects quoted numbers, and one that coerces them by default
        def unquotedInts = JsonMapper.builder()
                .addModule(new LazyvalJackson2Module())
                .addModule(new SimpleModule().addDeserializer(Integer.TYPE, new UnquotedIntDeserializer()))
                .build()
        def defaultInts = JsonMapper.builder().addModule(new LazyvalJackson2Module()).build()

        // a quoted number is no int token, so it reaches the int deserializer of the mapper reading it
        assert read(unquotedInts, '"2"', Quantity) instanceof MismatchedInputException
        assert read(defaultInts, '"2"', Quantity) == Quantity.of(2)
    }

    @Test
    void "a String deserializer registered on the mapper also reads String payloads"() {
        // a mapper whose String deserializer strips markup, as an application-wide input policy
        def sanitizing = JsonMapper.builder()
                .addModule(new LazyvalJackson2Module())
                .addModule(new SimpleModule().addDeserializer(String, new MarkupStrippingDeserializer()))
                .build()

        assert read(sanitizing, '"<b>summer25</b>"', CouponCode) == CouponCode.ofNullable('SUMMER25')
    }

    @Test
    void "a String serializer registered on the mapper also writes String payloads"() {
        // a mapper whose String serializer masks every value, as an application-wide output policy
        def masking = JsonMapper.builder()
                .addModule(new LazyvalJackson2Module())
                .addModule(new SimpleModule().addSerializer(String, new MaskingSerializer()))
                .build()

        assert masking.writeValueAsString(CouponCode.ofNullable('SUMMER25')) == '"***"'
    }

    /**
     * Returns either the read {@link Order} or the exception, so a failing condition prints what the
     * mapper actually produced instead of only "no exception thrown".
     */
    private Object read(String json) {
        return read(jsonMapper, json, Order)
    }

    private static Object read(ObjectMapper mapper, String json, Class<?> type) {
        try {
            return mapper.readValue(json, type)
        } catch (Exception e) {
            return e
        }
    }

    /** Strips markup from every String the mapper reads. */
    static class MarkupStrippingDeserializer extends StdDeserializer<String> {

        MarkupStrippingDeserializer() {
            super(String)
        }

        @Override
        String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return p.getValueAsString()?.replaceAll('<[^>]*>', '')
        }
    }

    /** Masks every String the mapper writes. */
    static class MaskingSerializer extends StdSerializer<String> {

        MaskingSerializer() {
            super(String)
        }

        @Override
        void serialize(String value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString('***')
        }
    }

    /** Reads int tokens only; Jackson's own int deserializer also coerces a quoted number. */
    static class UnquotedIntDeserializer extends StdDeserializer<Integer> {

        UnquotedIntDeserializer() {
            super(Integer)
        }

        @Override
        Integer deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return p.isExpectedNumberIntToken() ? p.getIntValue() : (Integer) ctxt.handleUnexpectedToken(Integer.TYPE, p)
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
