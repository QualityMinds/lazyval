package com.qualityminds.lazyval.integation

import com.qualityminds.lazyval.integration.shared.Quantity
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.validation.Validator
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotNull
import jakarta.validation.valueextraction.Unwrapping
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Kotlin - Quarkus Bean-Validation")
@QuarkusTest
class QuarkusBeanValidationIT {

    @Inject
    Validator validator

    @Test
    void "domain-primitive is unwrapped for default constraints"(){
        def some = new SomeClass(new Quantity(11))
        def violations = validator.validate(some)
        assert violations.size() == 1
        def v = violations.first()
        assert v.propertyPath.toString() == "quantity"
        assert v.constraintDescriptor.annotation.annotationType() == Max
        assert v.invalidValue == 11
    }

    @Test
    void "NonNull works for the call-site being null, not the internal value, when unwrap is skipped"(){
        def some = new SomeClass(null)
        def violations = validator.validate(some)
        assert violations.size() == 1
        def v = violations.first()
        assert v.propertyPath.toString() == "quantity"
        assert v.constraintDescriptor.annotation.annotationType() == NotNull
        assert v.invalidValue == null
    }

    @Test
    void "NonNull is skipped on the reference as unwrap is never called - no violation"(){
        def some = new NonNullSkippedClass(null)
        def violations = validator.validate(some)
        assert violations.size() == 0
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
