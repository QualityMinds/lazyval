package com.qualityminds.lazyval.integation.evaluation

import com.qualityminds.lazyval.integration.LazyvalJsonbAdapters
import com.qualityminds.lazyval.integration.domain.OrderDate
import com.qualityminds.lazyval.integration.shared.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import jakarta.json.bind.Jsonb
import jakarta.json.bind.JsonbBuilder
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Title

/**
 * Documents AI evaluation finding P12 (GitHub issue "Evaluation: processor", label evaluation); the features
 * intentionally fail on main.
 * <p>
 * Yasson hands an explicit JSON {@code null} to the adapter — {@code adaptFromJson(null)} — and the generated adapters
 * have no null guard, so {@code {"isbn":null}} fails with a {@code JsonbException} caused by a
 * {@code NullPointerException} in the domain-primitive's factory, while an absent property reads as {@code null}. An
 * explicit {@code null} has to read as {@code null} too.
 * <p>
 * Uses the module's generated {@code LazyvalJsonbAdapters.config()} with Yasson (test scope; Liberty provides the
 * JSON-B implementation in production).
 */
@Title("Evaluation P12 - JSON-B and explicit null")
class JsonbExplicitNullEvaluationSpec extends Specification {

    @Shared
    @AutoCleanup
    Jsonb jsonb = JsonbBuilder.create(LazyvalJsonbAdapters.config())

    def "P12 an explicit null #property is read as null"() {
        when:
        def dto = jsonb.fromJson("{\"$property\":null}", Dto)

        then:
        noExceptionThrown()
        dto[property] == null

        where:
        property << ['isbn', 'email', 'quantity', 'orderDate']
    }

    def "P12 an explicit null element of a List<Isbn> is read as null"() {
        when:
        def dto = jsonb.fromJson('{"isbns":[null]}', Dto)

        then:
        noExceptionThrown()
        dto.isbns == [null]
    }

    static class Dto {
        public Isbn isbn
        public EMail email
        public Quantity quantity
        public OrderDate orderDate
        public List<Isbn> isbns
    }
}
