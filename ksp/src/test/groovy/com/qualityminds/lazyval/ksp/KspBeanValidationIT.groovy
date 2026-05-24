package com.qualityminds.lazyval.ksp

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import org.eclipse.collections.api.factory.Lists
import spock.lang.*

import java.nio.file.Path

@Title("KSP Generator Integration - BeanValidation")
class KspBeanValidationIT extends Specification {

    public static final Dependency dependencyBeanValidation = new Dependency("jakarta.validation", "jakarta.validation-api", "3.1.0")

    @TempDir()
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    void "String type generates PatternValidator"(){
        given:
        def scenario = Scenario.Kotlin.isbn()
                .withDependencies(dependencyBeanValidation)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success("IsbnEmailValidator.kt", "IsbnPatternValidator.kt")
    }

    void "Numeric type generates Min and Max Validators"(){
        given:
        def scenario = Scenario.Kotlin.quantity()
                .withDependencies(dependencyBeanValidation)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success("QuantityMaxValidator.kt", "QuantityMinValidator.kt")
    }

    void "LocalDate type generates temporal validators"(){
        given:
        def scenario = Scenario.Kotlin.birthday()
                .withDependencies(dependencyBeanValidation)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(
                "BirthdayFutureOrPresentValidator.kt",
                "BirthdayFutureValidator.kt",
                "BirthdayPastOrPresentValidator.kt",
                "BirthdayPastValidator.kt")
    }

    void "Generated at correct default location when no override is given"(){
        given:
        def scenario = Scenario.Kotlin.isbn()
                .withDependencies(dependencyBeanValidation)

        when:
        testkitKotlin.run(projectDir, scenario)

        then: 'file is generated at correct location using base-package without layer'
        projectDir.resolve("build/generated/ksp/kotlin/test/IsbnPatternValidator.kt").toFile().exists()
    }

    void "Package override by generator works as expected"(){
        given:
        def scenario = Scenario.Kotlin.isbn()
                .withDependencies(dependencyBeanValidation)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.beanvalidation.package", "test.custom")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Kotlin.Success(Lists.immutable.of("IsbnEmailValidator.kt", "IsbnPatternValidator.kt"))

        and: 'file is at correct package'
        projectDir.resolve("build/generated/ksp/kotlin/test/custom/IsbnPatternValidator.kt").toFile().exists()
    }

    @Unroll("#scenario.name() compiles with BeanValidation")
    void "BeanValidation with all Scenarios"(){
        given:
        scenario.withDependencies(dependencyBeanValidation)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario << Scenario.Kotlin.all()
        expected = expectedResultFor(scenario)
    }

    private static Testresult.Kotlin expectedResultFor(def scenario) {
        def name = scenario.name()
        if (name == "Birthday.kt") {
            return new Testresult.Kotlin.Success(
                    "BirthdayFutureOrPresentValidator.kt",
                    "BirthdayFutureValidator.kt",
                    "BirthdayPastOrPresentValidator.kt",
                    "BirthdayPastValidator.kt")
        } else if (name == "Quantity.kt" || name == "NullableQuantity.kt") {
            def typeName = name.replace(".kt", "")
            return new Testresult.Kotlin.Success("${typeName}MaxValidator.kt", "${typeName}MinValidator.kt")
        } else {
            // String-wrapped types: Isbn, ProductId
            def typeName = name.replace(".kt", "")
            return new Testresult.Kotlin.Success("${typeName}EmailValidator.kt", "${typeName}PatternValidator.kt")
        }
    }
}
