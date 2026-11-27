package com.qualityminds.lazyval.integation.evaluation

import com.qualityminds.lazyval.integration.TestMapper
import com.qualityminds.lazyval.integration.domain.Order
import com.qualityminds.lazyval.integration.shared.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import org.mapstruct.factory.Mappers
import spock.lang.Specification
import spock.lang.Title

/**
 * Documents AI evaluation finding P20 (GitHub issue "Evaluation: processor", label evaluation); the feature
 * intentionally fails on main.
 * <p>
 * For a domain-primitive with a primitive payload the generated {@code LazyvalMapper} only offers
 * {@code int mapQuantityToInt(Quantity)}, which dereferences its argument. MapStruct calls it for a boxed
 * {@code Integer} target without a null check, so a {@code null} {@code Quantity} throws a
 * {@code NullPointerException} where every reference payload ({@code Isbn}, {@code CouponCode}, ...) maps to
 * {@code null}. A {@code null} domain-primitive has to map to a {@code null} boxed payload.
 * <p>
 * Uses this module's {@link TestMapper}, a MapStruct mapper that {@code uses = LazyvalMapper.class}, from the domain
 * {@link Order} to the client model, whose {@code quantity} is an {@code Integer}.
 */
@Title("Evaluation P20 - MapStruct and a null domain-primitive with a primitive payload")
class MapstructNullPrimitivePayloadEvaluationSpec extends Specification {

    TestMapper mapper = Mappers.getMapper(TestMapper)

    def "P20 a null Quantity is mapped to a null Integer instead of throwing"() {
        given: 'an order without a quantity, and without a coupon code as the control case'
        def order = new Order(UUID.randomUUID(), Isbn.parse('978-3-16-148410-0'), null, new EMail('a@b.example'),
                Currency.getInstance('EUR'), null, null)

        when:
        def client = mapper.toClientOrder(order)

        then:
        noExceptionThrown()
        client.quantity == null
        client.couponCode == null
    }
}
