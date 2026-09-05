package com.qualityminds.lazyval.integation

import com.qualityminds.lazyval.integration.shared.Quantity
import jakarta.validation.Validation
import jakarta.validation.Validator
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotNull
import jakarta.validation.valueextraction.Unwrapping
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Title

@Title("Kotlin - JakartaEE Bean-Validation")
class JeeBeanValidationIT extends Specification {

    @Shared
    Validator validator = Validation.buildDefaultValidatorFactory().validator

    def "domain-primitive is unwrapped for default constraints"() {
        given:
        def some = new SomeClass(Quantity.of(11))

        when:
        def violations = validator.validate(some)

        then:
        violations.size() == 1
        with(violations.first()) {
            propertyPath.toString() == "quantity"
            constraintDescriptor.annotation.annotationType() == Max
            invalidValue == 11
        }
    }

    def "NonNull works for the call-site being null, not the internal value, when unwrap is skipped"() {
        given:
        def some = new SomeClass(null)

        when:
        def violations = validator.validate(some)

        then:
        violations.size() == 1
        with(violations.first()) {
            propertyPath.toString() == "quantity"
            constraintDescriptor.annotation.annotationType() == NotNull
            invalidValue == null
        }
    }

    def "NonNull is skipped on the reference as unwrap is never called - no violation"() {
        given:
        def some = new NonNullSkippedClass(null)

        when:
        def violations = validator.validate(some)

        then:
        violations.size() == 0
    }

    static class SomeClass {
        @NotNull(payload = Unwrapping.Skip.class)
        @Max(10)
        Quantity quantity

        SomeClass(Quantity quantity) {
            this.quantity = quantity
        }
    }

    static class NonNullSkippedClass {
        @NotNull
        Quantity quantity

        NonNullSkippedClass(Quantity quantity) {
            this.quantity = quantity
        }
    }
}
