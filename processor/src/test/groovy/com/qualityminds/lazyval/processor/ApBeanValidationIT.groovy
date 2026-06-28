package com.qualityminds.lazyval.processor

import com.qualityminds.lazyval.testkit.Approval
import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title

import java.nio.file.Path

@Title("Generator Integration - BeanValidation")
class ApBeanValidationIT extends Specification {

    public static final Dependency dependencyBeanValidation = new Dependency("jakarta.validation", "jakarta.validation-api", "3.1.0")

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    void "BeanValidation with combined Scenarios"() {
        given: 'a compiler run with all sources'
        def scenario = Scenario.Java.combined().withDependencies(dependencyBeanValidation)

        and: 'a defined approval for each generated ValueExtractor and the ServiceLoader registration'
        List<Approval> approvals = [
                Approval.JavaSource.at("test/IsbnValueExtractor.java", "approvals/beanvalidation/IsbnValueExtractor.java"),
                Approval.JavaSource.at("test/IdsProductIdValueExtractor.java", "approvals/beanvalidation/IdsProductIdValueExtractor.java"),
                Approval.JavaSource.at("test/QuantityValueExtractor.java", "approvals/beanvalidation/QuantityValueExtractor.java"),
                Approval.JavaSource.at("test/OrderDateValueExtractor.java", "approvals/beanvalidation/OrderDateValueExtractor.java"),
                Approval.ServiceLoader.of("jakarta.validation.valueextraction.ValueExtractor",
                        "test.IdsProductIdValueExtractor",
                        "test.IsbnValueExtractor",
                        "test.OrderDateValueExtractor",
                        "test.QuantityValueExtractor")
        ]

        when:
        def result = testkitJava.run(projectDir, scenario, approvals)

        then:
        result == Testresult.Java.Approved.of(approvals)
    }

    void "Package override by generator works as expected"() {
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
        testkitJava.generatedSourcePath(projectDir, "test/custom/IsbnValueExtractor.java").toFile().exists()
    }
}
