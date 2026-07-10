package com.qualityminds.lazyval.integation

import com.qualityminds.lazyval.integration.shared.Quantity
import jakarta.validation.Validation
import jakarta.validation.Validator
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotNull
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Title

@Title("Kotlin - JakartaEE Bean-Validation")
class JeeBeanValidationIT extends Specification {

    @Shared
    Validator validator = Validation.buildDefaultValidatorFactory().validator

    def "default constraint is running on domain-primitive and returning a violation"() {
        given:
        def some = new SomeClass(new Quantity(11))

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

    static class SomeClass {
        @NotNull
        @Max(10)
        Quantity quantity

        SomeClass(Quantity quantity) {
            this.quantity = quantity
        }
    }
}
