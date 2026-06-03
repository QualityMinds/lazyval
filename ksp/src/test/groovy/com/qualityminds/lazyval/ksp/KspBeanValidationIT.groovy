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

    @Unroll("#scenario.name() compiles and generated #expectedFiles")
    void "String type generates Pattern- and EMail-Validator"(){
        given:
        scenario.withDependencies(dependencyBeanValidation)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(expectedFiles)

        where:
        scenario                || expectedFiles
        Scenario.Kotlin.isbn()  || ["IsbnEmailValidator.kt", "IsbnPatternValidator.kt"]
        Scenario.Kotlin.ids()   || ["IdsProductIdEmailValidator.kt", "IdsProductIdPatternValidator.kt"]
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
        def scenario = Scenario.Kotlin.orderDate()
                .withDependencies(dependencyBeanValidation)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(
                "OrderDateFutureOrPresentValidator.kt",
                "OrderDateFutureValidator.kt",
                "OrderDatePastOrPresentValidator.kt",
                "OrderDatePastValidator.kt")
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
        result == new Testresult.Kotlin.Success(["IsbnEmailValidator.kt", "IsbnPatternValidator.kt"])

        and: 'file is at correct package'
        projectDir.resolve("build/generated/ksp/kotlin/test/custom/IsbnPatternValidator.kt").toFile().exists()
    }

}
