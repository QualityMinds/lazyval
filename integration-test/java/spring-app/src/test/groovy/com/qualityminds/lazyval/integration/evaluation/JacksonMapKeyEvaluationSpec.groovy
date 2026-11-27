package com.qualityminds.lazyval.integration.evaluation

import com.qualityminds.lazyval.integration.LazyvalJacksonModule
import com.qualityminds.lazyval.integration.shared.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Title
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper

/**
 * Documents AI evaluation finding P6 (GitHub issue "Evaluation: processor", label evaluation); the features
 * intentionally fail on main.
 * <p>
 * The generated module registers value (de)serializers only, no key (de)serializers. A domain-primitive used as a
 * {@code Map} key is therefore written with its {@code toString()} — {@code {"ISBN{978-3-16-148410-0}":1}} — and read
 * back through any single-{@code String} constructor Jackson finds, private ones included, which bypasses the factory:
 * {@code {"<script>alert(1)</script>":1}} becomes an {@code Isbn} that {@code Isbn.parse} would have rejected. One
 * without such a constructor, like {@code Quantity}, cannot be read as a key at all. A domain-primitive has to be a
 * key the way it is a value: written as its payload, read through its factory or constructor.
 */
@Title("Evaluation P6 - domain-primitives as Jackson map keys")
class JacksonMapKeyEvaluationSpec extends Specification {

    @Shared
    JsonMapper mapper = JsonMapper.builder().addModule(new LazyvalJacksonModule()).build()

    def "P6 #key.class.simpleName as a map key is written as its payload"() {
        expect:
        mapper.writeValueAsString([(key): 1]) == "{\"$payload\":1}"

        where:
        key                             | payload
        Isbn.parse('978-3-16-148410-0') | '978-3-16-148410-0'
        new EMail('a@b.example')        | 'a@b.example'
        new Quantity(2)                 | '2'
    }

    def "P6 an Isbn map key is read through Isbn.parse, which rejects an invalid key"() {
        when:
        def read = mapper.readValue('{"<script>alert(1)</script>":1}', new TypeReference<Map<Isbn, Integer>>() {})

        then:
        thrown(RuntimeException)
    }

    def "P6 a Quantity map key is read through its constructor"() {
        when:
        def read = mapper.readValue('{"2":1}', new TypeReference<Map<Quantity, Integer>>() {})

        then:
        read == [(new Quantity(2)): 1]
    }
}
