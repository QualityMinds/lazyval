package com.qualityminds.lazyval.integation

import com.qualityminds.lazyval.integration.shared.Quantity
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.validation.Validator
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Kotlin - Quarkus Bean-Validation")
@QuarkusTest
class QuarkusBeanValidationIT {

    @Inject
    Validator validator

    @Test
    void "default constraint is running on domain-primitive and returning a violation"(){
        def some = new SomeClass(new Quantity(11))
        def violations = validator.validate(some)
        assert violations.size() == 1
        def v = violations.first()
        assert v.propertyPath.toString() == "quantity"
        assert v.constraintDescriptor.annotation.annotationType() == Max
        assert v.invalidValue == 11   // the *unwrapped* value — proves @UnwrapByDefault worked
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
