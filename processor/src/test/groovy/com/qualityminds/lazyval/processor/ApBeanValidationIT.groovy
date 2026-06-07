package com.qualityminds.lazyval.processor

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.*

import java.nio.file.Path

@Title("Generator Integration - BeanValidation")
class ApBeanValidationIT extends Specification {

    public static final Dependency dependencyBeanValidation = new Dependency("jakarta.validation", "jakarta.validation-api", "3.1.0")

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    @Unroll("#scenario.name() compiles and generated #expectedFiles")
    void "String type generates Pattern- and EMail-Validator"(){
        given:
        scenario.withDependencies(dependencyBeanValidation)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(expectedFiles)

        where:
        scenario                || expectedFiles
        Scenario.Java.isbn()    || ["IsbnEmailValidator.java", "IsbnPatternValidator.java"]
        Scenario.Java.ids()     || ["IdsProductIdEmailValidator.java", "IdsProductIdPatternValidator.java"]
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
        def scenario = Scenario.Java.orderDate()
                .withDependencies(dependencyBeanValidation)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(
                "OrderDateFutureOrPresentValidator.java",
                "OrderDateFutureValidator.java",
                "OrderDatePastOrPresentValidator.java",
                "OrderDatePastValidator.java")
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
        result == new Testresult.Java.Success("IsbnEmailValidator.java", "IsbnPatternValidator.java")

        and: 'file is at correct package'
        projectDir.resolve("build/generated/test/custom/IsbnPatternValidator.java").toFile().exists()
    }
}
