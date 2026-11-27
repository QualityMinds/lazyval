package com.qualityminds.lazyval.integration.evaluation

import com.qualityminds.lazyval.integration.LazyvalJsonbAdapters
import com.qualityminds.lazyval.integration.domain.OrderDate
import com.qualityminds.lazyval.integration.shared.CouponCode
import com.qualityminds.lazyval.integration.shared.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import jakarta.json.bind.Jsonb
import jakarta.json.bind.JsonbBuilder
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Title
import spock.lang.Unroll

/**
 * AI evaluation finding K17 (GitHub issue "Evaluation: ksp", label evaluation). This specification intentionally
 * fails on main: it documents the expected behaviour, not the current one.
 * <p>
 * Yasson hands an explicit JSON {@code null} to the adapter, and the generated Kotlin adapters declare their
 * parameter non-null ({@code adaptFromJson(value: String)}), so on main reading {@code {"isbn":null}} fails with
 * "Parameter specified as non-null is null" instead of leaving the property {@code null}, as an absent property
 * does. The same holds for {@code null} elements of a list.
 * <p>
 * No application server: Yasson from the test classpath with {@code LazyvalJsonbAdapters.config()}.
 */
@Title("Evaluation K17 - explicit JSON null through the generated JSON-B adapters")
class K17JsonbExplicitNullSpec extends Specification {

    @Shared
    @AutoCleanup
    Jsonb jsonb = JsonbBuilder.create(LazyvalJsonbAdapters.config())

    def setupSpec() {
        // precondition: an absent property is null, so it is the explicit null that differs
        def empty = jsonb.fromJson('{}', Dto)
        assert [empty.isbn, empty.quantity, empty.email, empty.couponCode, empty.orderDate, empty.isbns].every { it == null }
    }

    @Unroll("K17 an explicit JSON null for #property is read as null")
    void "K17 an explicit JSON null for a domain-primitive property is read as null"() {
        when:
        def dto = jsonb.fromJson("{\"$property\":null}", Dto)

        then:
        noExceptionThrown()
        dto."$property" == null

        where:
        property << ['isbn', 'quantity', 'email', 'couponCode', 'orderDate']
    }

    void "K17 an explicit JSON null in a list of domain-primitives is read as a null element"() {
        when:
        def dto = jsonb.fromJson('{"isbns":[null]}', Dto)

        then:
        noExceptionThrown()
        dto.isbns == [null]
    }

    static class Dto {
        public Isbn isbn
        public Quantity quantity
        public EMail email
        public CouponCode couponCode
        public OrderDate orderDate
        public List<Isbn> isbns
    }
}
