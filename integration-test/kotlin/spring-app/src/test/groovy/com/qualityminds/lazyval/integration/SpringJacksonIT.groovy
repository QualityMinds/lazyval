package com.qualityminds.lazyval.integration

import com.fasterxml.jackson.annotation.JsonFormat
import com.qualityminds.lazyval.integration.boundary.rest.model.Order
import com.qualityminds.lazyval.integration.domain.OrderDate
import com.qualityminds.lazyval.integration.shared.CouponCode
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Title
import spock.lang.Unroll
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.StdSerializer

import java.time.LocalDate

/**
 * Reads the generated {@link Order} model through the application's own {@link ObjectMapper}, i.e. the
 * Spring Boot configuration with the {@code LazyvalJacksonModule} picked up via ServiceLoader.
 *
 * A domain-primitive is a scalar on the wire, so any other token has to be rejected the way Jackson rejects
 * it for the payload type itself. {@code orderDate} is the control case: its deserializer delegates to
 * Jackson's {@code LocalDate} deserializer instead of reading the token itself.
 */
@Title("Kotlin - Spring Jackson")
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
            isbn.value == '978-3-16-148410-0'
            // the payload is the value class Amount, whose getter is mangled - compare the whole value instead
            quantity == Quantity.of(2)
            couponCode.value == 'SUMMER25'
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

    /*
     * The next two features build mappers of their own: each resolves the deserializer a domain-primitive
     * delegates to against its own configuration, so what one mapper resolved must not be what another
     * reads with. Both mappers are checked, since either one may be the first to resolve, or neither if
     * the application's mapper already did in an earlier feature.
     */

    def "a LocalDate format configured on one mapper is not applied by another"() {
        given: 'a mapper reading LocalDate as dd.MM.yyyy, and one with the ISO default'
        def dottedDates = JsonMapper.builder()
                .addModule(new LazyvalJacksonModule())
                .withConfigOverride(LocalDate, { it.setFormat(JsonFormat.Value.forPattern('dd.MM.yyyy')) })
                .build()
        def isoDates = JsonMapper.builder().addModule(new LazyvalJacksonModule()).build()

        expect:
        read(dottedDates, '"01.01.2026"', OrderDate) == new OrderDate(LocalDate.of(2026, 1, 1))
        read(isoDates, '"2026-01-01"', OrderDate) == new OrderDate(LocalDate.of(2026, 1, 1))
    }

    def "an int deserializer registered on one mapper is not applied by another"() {
        given: 'a mapper whose own int deserializer rejects quoted numbers, and one that coerces them by default'
        def unquotedInts = JsonMapper.builder()
                .addModule(new LazyvalJacksonModule())
                .addModule(new SimpleModule().addDeserializer(Integer.TYPE, new UnquotedIntDeserializer()))
                .build()
        def defaultInts = JsonMapper.builder().addModule(new LazyvalJacksonModule()).build()

        expect: 'a quoted number is no int token, so it reaches the int deserializer of the mapper reading it'
        read(unquotedInts, '"2"', Quantity) instanceof MismatchedInputException
        read(defaultInts, '"2"', Quantity) == Quantity.of(2)
    }

    def "a String deserializer registered on the mapper also reads String payloads"() {
        given: 'a mapper whose String deserializer strips markup, as an application-wide input policy'
        def sanitizing = JsonMapper.builder()
                .addModule(new LazyvalJacksonModule())
                .addModule(new SimpleModule().addDeserializer(String, new MarkupStrippingDeserializer()))
                .build()

        expect:
        read(sanitizing, '"<b>summer25</b>"', CouponCode) == CouponCode.ofNullable('SUMMER25')
    }

    def "a String deserializer reading blank input as null makes #type.simpleName null"() {
        given: 'a mapper whose String deserializer reads blank input as null, as an application-wide input policy'
        def blankAsNull = JsonMapper.builder()
                .addModule(new LazyvalJacksonModule())
                .addModule(new SimpleModule().addDeserializer(String, new BlankAsNullDeserializer()))
                .build()

        expect: 'null, as for JSON null, without calling the factory: CouponCode.ofNullable accepts null, Isbn.parse rejects it'
        read(blankAsNull, '"   "', type) == null

        where:
        type << [CouponCode, Isbn]
    }

    def "an empty string is a null OrderDate, as Jackson reads it as a null LocalDate"() {
        expect: 'Jackson reads "" as null for LocalDate by default, so OrderDate is null rather than built around null'
        read(jsonMapper, '""', LocalDate) == null
        read(jsonMapper, '""', OrderDate) == null
    }

    def "a String serializer registered on the mapper also writes String payloads"() {
        given: 'a mapper whose String serializer masks every value, as an application-wide output policy'
        def masking = JsonMapper.builder()
                .addModule(new LazyvalJacksonModule())
                .addModule(new SimpleModule().addSerializer(String, new MaskingSerializer()))
                .build()

        expect:
        masking.writeValueAsString(CouponCode.ofNullable('SUMMER25')) == '"***"'
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
        String deserialize(JsonParser p, DeserializationContext ctxt) {
            return p.getValueAsString()?.replaceAll('<[^>]*>', '')
        }
    }

    /** Reads blank input as null, as an application might for optional text. */
    static class BlankAsNullDeserializer extends StdDeserializer<String> {

        BlankAsNullDeserializer() {
            super(String)
        }

        @Override
        String deserialize(JsonParser p, DeserializationContext ctxt) {
            def text = p.getValueAsString()
            return text?.isBlank() ? null : text
        }
    }

    /** Masks every String the mapper writes. */
    static class MaskingSerializer extends StdSerializer<String> {

        MaskingSerializer() {
            super(String)
        }

        @Override
        void serialize(String value, JsonGenerator gen, SerializationContext provider) {
            gen.writeString('***')
        }
    }

    /** Reads int tokens only; Jackson's own int deserializer also coerces a quoted number. */
    static class UnquotedIntDeserializer extends StdDeserializer<Integer> {

        UnquotedIntDeserializer() {
            super(Integer)
        }

        @Override
        Integer deserialize(JsonParser p, DeserializationContext ctxt) {
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
