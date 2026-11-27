package com.qualityminds.lazyval.integration.evaluation

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.qualityminds.lazyval.integration.LazyvalJacksonModule
import com.qualityminds.lazyval.integration.shared.Isbn
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Title
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.DefaultTyping
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator

/**
 * Documents AI evaluation finding P14, runtime part (GitHub issue "Evaluation: processor", label evaluation); the
 * feature intentionally fails on main.
 * <p>
 * The generated serializers do not implement {@code serializeWithType}, so as soon as type information is written —
 * default typing, as used by e.g. Spring's {@code GenericJacksonJsonRedisSerializer}, or {@code @JsonTypeInfo} on an
 * {@code Object} property — serializing a domain-primitive fails with "Type id handling ... not implemented". A
 * mapper with default typing has to write a domain-primitive, and read it back.
 */
@Title("Evaluation P14 - domain-primitives under polymorphic type handling")
class JacksonPolymorphicTypingEvaluationSpec extends Specification {

    @Shared
    JsonMapper mapper = JsonMapper.builder()
            .addModule(new LazyvalJacksonModule())
            .activateDefaultTyping(
                    BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object).build(),
                    DefaultTyping.JAVA_LANG_OBJECT,
                    JsonTypeInfo.As.PROPERTY)
            .build()

    def "P14 an Isbn held by an Object-typed value survives a round trip under default typing"() {
        given:
        def holder = [isbn: Isbn.parse('978-3-16-148410-0')]

        when:
        def json = mapper.writeValueAsString(holder)

        then:
        noExceptionThrown()
        mapper.readValue(json, new TypeReference<Map<String, Object>>() {}) == holder
    }
}
