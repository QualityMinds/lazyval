package com.qualityminds.lazyval.integation.evaluation

import com.qualityminds.lazyval.integration.shared.CouponCode
import com.qualityminds.lazyval.integration.shared.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import jakarta.validation.Validation
import jakarta.validation.Validator
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Title

/**
 * Documents AI evaluation finding P2 (GitHub issue "Evaluation: processor", label evaluation); the features
 * intentionally fail on main.
 * <p>
 * The generated value extractors are {@code @UnwrapByDefault}, so every constraint on a domain-primitive is moved to
 * its payload — and Hibernate Validator never calls a value extractor for a {@code null} reference, so the constraint
 * is not evaluated at all. A constraint that rejects {@code null} has to reject a {@code null} domain-primitive too,
 * instead of the validation failing open.
 * <p>
 * beanvalidation.adoc ("NonNull") documents the {@code @NotNull} case with {@code Unwrapping.Skip} as workaround, and
 * {@code JeeBeanValidationIT} pins it; {@code @NotBlank}, {@code @NotEmpty} and container elements are not covered by
 * that note. The validator is bootstrapped the way {@code JeeBeanValidationIT} does it, with the module's
 * ServiceLoader-registered extractors.
 */
@Title("Evaluation P2 - Bean Validation of null domain-primitive references")
class BeanValidationNullEvaluationSpec extends Specification {

    @Shared
    Validator validator = Validation.buildDefaultValidatorFactory().validator

    def "P2 @#constraint.simpleName on a null #type.simpleName is reported as a violation"() {
        when:
        def violations = validator.validate(bean)

        then:
        violations*.propertyPath*.toString() == [property]
        violations*.constraintDescriptor*.annotation*.annotationType() == [constraint]

        where:
        bean                     | property     | constraint | type
        new NotNullIsbn()        | 'isbn'       | NotNull    | Isbn
        new NotBlankEMail()      | 'email'      | NotBlank   | EMail
        new NotEmptyCouponCode() | 'couponCode' | NotEmpty   | CouponCode
    }

    def "P2 a null element of a List<@NotNull Isbn> is reported as a violation"() {
        when:
        def violations = validator.validate(new NotNullIsbnElements())

        then:
        violations.size() == 1
        with(violations.first()) {
            propertyPath.toString().startsWith('isbns')
            constraintDescriptor.annotation.annotationType() == NotNull
        }
    }

    static class NotNullIsbn {
        @NotNull
        private Isbn isbn
    }

    static class NotBlankEMail {
        @NotBlank
        private EMail email
    }

    static class NotEmptyCouponCode {
        @NotEmpty
        private CouponCode couponCode
    }

    static class NotNullIsbnElements {
        private List<@NotNull Isbn> isbns = [null]
    }
}
