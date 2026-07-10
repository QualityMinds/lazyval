package com.qualityminds.lazyval.integration

import com.qualityminds.lazyval.integration.shared.Quantity
import jakarta.validation.Validator
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Title

@Title("Java - Spring Bean-Validation")
@SpringBootTest
class SpringBeanValidationIT extends AbstractIT {

    @Autowired
    Validator validator

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
