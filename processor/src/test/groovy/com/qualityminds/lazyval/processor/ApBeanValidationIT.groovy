package com.qualityminds.lazyval.processor

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import org.eclipse.collections.api.factory.Lists
import spock.lang.*

import java.nio.file.Path

@Title("Generator Integration - BeanValidation")
class ApBeanValidationIT extends Specification {

    public static final Dependency dependencyBeanValidation = new Dependency("jakarta.validation", "jakarta.validation-api", "3.1.0")

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    void "String type generates PatternValidator"(){
        given:
        def scenario = Scenario.Java.isbn()
                .withDependencies(dependencyBeanValidation)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success("IsbnEmailValidator.java", "IsbnPatternValidator.java")
    }

    void "Numeric type generates Min and Max Validators"(){
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencyBeanValidation)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success("QuantityMaxValidator.java", "QuantityMinValidator.java")
    }

    void "LocalDate type generates temporal validators"(){
        given:
        def scenario = Scenario.Java.birthday()
                .withDependencies(dependencyBeanValidation)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(
                "BirthdayFutureOrPresentValidator.java",
                "BirthdayFutureValidator.java",
                "BirthdayPastOrPresentValidator.java",
                "BirthdayPastValidator.java")
    }

    void "Generated at correct default location when no override is given"(){
        given:
        def scenario = Scenario.Java.isbn()
                .withDependencies(dependencyBeanValidation)

        when:
        testkitJava.run(projectDir, scenario)

        then: 'file is generated at correct location using base-package without layer'
        projectDir.resolve("build/generated/test/IsbnPatternValidator.java").toFile().exists()
    }

    void "Package override by generator works as expected"(){
        given:
        def scenario = Scenario.Java.isbn()
                .withDependencies(dependencyBeanValidation)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.beanvalidation.package", "test.custom")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Java.Success(Lists.immutable.of("IsbnEmailValidator.java", "IsbnPatternValidator.java"))

        and: 'file is at correct package'
        projectDir.resolve("build/generated/test/custom/IsbnPatternValidator.java").toFile().exists()
    }

    @Unroll("#scenario.name() compiles with BeanValidation")
    void "BeanValidation with all Scenarios"(){
        given:
        scenario.withDependencies(dependencyBeanValidation)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario << Scenario.Java.all()
        expected = expectedResultFor(scenario)
    }

    private static Testresult.Java expectedResultFor(def scenario) {
        def name = scenario.name()
        if (name == "Birthday.java") {
            return new Testresult.Java.Success(
                    "BirthdayFutureOrPresentValidator.java",
                    "BirthdayFutureValidator.java",
                    "BirthdayPastOrPresentValidator.java",
                    "BirthdayPastValidator.java")
        } else if (name == "Quantity.java") {
            return new Testresult.Java.Success("QuantityMaxValidator.java", "QuantityMinValidator.java")
        } else {
            // String-wrapped types: Isbn, ProductId, SocialSecurityNumber
            def typeName = name.replace(".java", "")
            return new Testresult.Java.Success("${typeName}EmailValidator.java", "${typeName}PatternValidator.java")
        }
    }
}
