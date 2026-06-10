package com.qualityminds.lazyval.processor

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.*

import java.nio.file.Files
import java.nio.file.Path

@Title("Generator Integration - BeanValidation")
class ApBeanValidationIT extends Specification {

    public static final Dependency dependencyBeanValidation = new Dependency("jakarta.validation", "jakarta.validation-api", "3.1.0")

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    @Unroll("#scenario.name() compiles and generates #expectedFile")
    void "each domain-primitive generates a single ValueExtractor"(){
        given:
        scenario.withDependencies(dependencyBeanValidation)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(expectedFile)

        and: 'contains @Generated'
        Files.readString(projectDir.resolve("build/generated/test/$expectedFile")).contains("@Generated")

        where:
        scenario                || expectedFile
        Scenario.Java.isbn()    || "IsbnValueExtractor.java"
        Scenario.Java.ids()     || "IdsProductIdValueExtractor.java"
        Scenario.Java.quantity()|| "QuantityValueExtractor.java"
        Scenario.Java.orderDate()|| "OrderDateValueExtractor.java"
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
        result == new Testresult.Java.Success("IsbnValueExtractor.java")

        and: 'file is at correct package'
        projectDir.resolve("build/generated/test/custom/IsbnValueExtractor.java").toFile().exists()
    }
}
