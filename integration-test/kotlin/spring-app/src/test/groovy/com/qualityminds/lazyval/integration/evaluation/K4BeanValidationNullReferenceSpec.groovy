package com.qualityminds.lazyval.integration.evaluation

import com.qualityminds.lazyval.integration.shared.CouponCode
import com.qualityminds.lazyval.integration.shared.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import jakarta.validation.Validation
import jakarta.validation.Validator
import jakarta.validation.ValidatorFactory
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Title
import spock.lang.Unroll

/**
 * AI evaluation finding K4 (GitHub issue "Evaluation: ksp", label evaluation). This specification intentionally
 * fails on main: it documents the expected behaviour, not the current one.
 * <p>
 * The generated value extractors are {@code @UnwrapByDefault}, so every constraint on a domain-primitive is moved
 * to the extracted payload, and Hibernate Validator does not call an extractor for a {@code null} container. A
 * constraint whose contract includes rejecting {@code null} therefore matches nothing when the reference is
 * {@code null}: {@code @NotBlank}, {@code @NotEmpty}, {@code @NotNull}, also on a container element. The generated
 * {@code null} branch in {@code extractValues} is never reached.
 * <p>
 * {@code beanvalidation.adoc} ("NonNull") documents this for {@code @NotNull}, with {@code Unwrapping.Skip} as the
 * way out, and {@code SpringBeanValidationIT} pins "no violation" for that case; the evaluation treats it as fail-open
 * all the same. No application context: Hibernate Validator from the test classpath finds the generated extractors
 * through {@code META-INF/services}, as any provider does. Each row brings a control with the payload type in place
 * of the domain-primitive, which shows the constraint itself does reject {@code null}.
 */
@Title("Evaluation K4 - Bean Validation on null domain-primitive references")
class K4BeanValidationNullReferenceSpec extends Specification {

    @Shared
    @AutoCleanup
    ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()

    @Shared
    Validator validator = validatorFactory.validator

    @Unroll("K4 #constraint on a null #type is a violation, as on a null #payload")
    void "K4 a constraint that rejects null rejects a null domain-primitive reference"() {
        expect: 'precondition: the same constraint on the payload type reports the null'
        validator.validate(control)*.propertyPath*.toString() == [path]

        and:
        validator.validate(bean)*.propertyPath*.toString() == [path]

        where:
        constraint       | type           | payload          | bean                     | control                 | path
        '@NotNull'       | 'Isbn'         | 'String'         | new NotNullIsbn()        | new NotNullString()     | 'value'
        '@NotNull'       | 'Quantity'     | 'Integer'        | new NotNullQuantity()    | new NotNullInteger()    | 'value'
        '@NotBlank'      | 'EMail'        | 'String'         | new NotBlankEMail()      | new NotBlankString()    | 'value'
        '@NotEmpty'      | 'CouponCode'   | 'String'         | new NotEmptyCouponCode() | new NotEmptyString()    | 'value'
        'List<@NotNull>' | 'Isbn element' | 'String element' | new NullIsbnElement()    | new NullStringElement() | 'values[0].<list element>'
    }

    static class NotNullIsbn {
        @NotNull
        Isbn value
    }

    static class NotNullString {
        @NotNull
        String value
    }

    static class NotNullQuantity {
        @NotNull
        Quantity value
    }

    static class NotNullInteger {
        @NotNull
        Integer value
    }

    static class NotBlankEMail {
        @NotBlank
        EMail value
    }

    static class NotBlankString {
        @NotBlank
        String value
    }

    static class NotEmptyCouponCode {
        @NotEmpty
        CouponCode value
    }

    static class NotEmptyString {
        @NotEmpty
        String value
    }

    static class NullIsbnElement {
        List<@NotNull Isbn> values = [null]
    }

    static class NullStringElement {
        List<@NotNull String> values = [null]
    }
}
