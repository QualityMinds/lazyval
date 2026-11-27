package com.qualityminds.lazyval.integration.evaluation

import com.qualityminds.lazyval.integration.boundary.persistence.LazyvalSpringDataConfiguration.RedisSerializers
import spock.lang.Specification
import spock.lang.Title
import spock.lang.Unroll

/**
 * AI evaluation finding K19 (GitHub issue "Evaluation: ksp", label evaluation). This specification intentionally
 * fails on main: it documents the expected behaviour, not the current one.
 * <p>
 * The generated Spring Data Redis serializers write {@code null} as an empty byte array, which is what Spring's own
 * serializers do, but do not read an empty array back as {@code null}, which Spring's own serializers do. The empty
 * payload reaches the factory or constructor instead: on main {@code Isbn.parse("")} and {@code EMail("")} throw,
 * {@code ORDER_DATE} fails with a {@code ConversionFailedException}. So a {@code null} written through
 * {@code RedisTemplate} or a cache cannot be read back. No application context: the serializers are plain
 * {@code @JvmField}s.
 */
@Title("Evaluation K19 - null round trip through the generated Redis serializers")
class K19RedisSerializerNullSpec extends Specification {

    @Unroll("K19 RedisSerializers.#name reads back the null it wrote")
    void "K19 a null written by a generated Redis serializer is read back as null"() {
        given:
        def serializer = RedisSerializers."$name"

        when:
        def written = serializer.serialize(null)
        def read = serializer.deserialize(written)

        then:
        noExceptionThrown()
        read == null

        where:
        name << ['ISBN', 'E_MAIL', 'ORDER_DATE']
    }
}
