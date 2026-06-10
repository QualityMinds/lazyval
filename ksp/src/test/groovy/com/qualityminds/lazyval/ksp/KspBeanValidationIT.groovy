package com.qualityminds.lazyval.ksp

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.*

import java.nio.file.Files
import java.nio.file.Path

@Title("KSP Generator Integration - BeanValidation")
class KspBeanValidationIT extends Specification {

    public static final Dependency dependencyJakartaAnnotations = new Dependency("jakarta.annotation", "jakarta.annotation-api", "3.0.0")
    public static final Dependency dependencyBeanValidation = new Dependency("jakarta.validation", "jakarta.validation-api", "3.1.0")

    @TempDir()
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    @Unroll("#scenario.name() compiles and generates #implFile and #baseFile")
    void "each domain-primitive generates a Java base class and a Kotlin ValueExtractor"(){
        given:
        scenario.withDependencies(dependencyBeanValidation, dependencyJakartaAnnotations)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(baseFile, implFile)

        and: 'Java base contains @Generated'
        Files.readString(projectDir.resolve("build/generated/ksp/java/test/$baseFile")).contains("@Generated")

        and: 'Kotlin impl contains @Generated'
        Files.readString(projectDir.resolve("build/generated/ksp/kotlin/test/$implFile")).contains("@Generated")

        where:
        scenario                   || baseFile                              | implFile
        Scenario.Kotlin.isbn()     || "IsbnValueExtractorBase.java"         | "IsbnValueExtractor.kt"
        Scenario.Kotlin.ids()      || "IdsProductIdValueExtractorBase.java" | "IdsProductIdValueExtractor.kt"
        Scenario.Kotlin.quantity() || "QuantityValueExtractorBase.java"     | "QuantityValueExtractor.kt"
        Scenario.Kotlin.orderDate()|| "OrderDateValueExtractorBase.java"    | "OrderDateValueExtractor.kt"
    }

    void "Package override by generator works as expected"(){
        given:
        def scenario = Scenario.Kotlin.isbn()
                .withDependencies(dependencyBeanValidation, dependencyJakartaAnnotations)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.beanvalidation.package", "test.custom")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Kotlin.Success("IsbnValueExtractorBase.java", "IsbnValueExtractor.kt")

        and: 'files are at the correct package'
        projectDir.resolve("build/generated/ksp/java/test/custom/IsbnValueExtractorBase.java").toFile().exists()
        projectDir.resolve("build/generated/ksp/kotlin/test/custom/IsbnValueExtractor.kt").toFile().exists()
    }
}
