package com.qualityminds.lazyval.ksp

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

@Title("KSP Generator Integration - BeanValidation")
class KspBeanValidationIT extends Specification {

    public static final Dependency dependencyJakartaAnnotations = new Dependency("jakarta.annotation", "jakarta.annotation-api", "3.0.0")
    public static final Dependency dependencyBeanValidation = new Dependency("jakarta.validation", "jakarta.validation-api", "3.1.0")

    @TempDir()
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    void "BeanValidation with combined Scenarios"() {
        given: 'a compiler run with all sources'
        def scenario = Scenario.Kotlin.combined()
                .withDependencies(dependencyBeanValidation, dependencyJakartaAnnotations)

        and: 'a defined approval for each generated Kotlin impl, Java base and the ServiceLoader registration'
        List<Approval.ForKotlin> approvals = [
                Approval.KotlinSource.at("test/IsbnValueExtractor.kt", "approvals/beanvalidation/IsbnValueExtractor.kt"),
                Approval.KotlinSource.at("test/IdsProductIdValueExtractor.kt", "approvals/beanvalidation/IdsProductIdValueExtractor.kt"),
                Approval.KotlinSource.at("test/NullableQuantityValueExtractor.kt", "approvals/beanvalidation/NullableQuantityValueExtractor.kt"),
                Approval.KotlinSource.at("test/QuantityValueExtractor.kt", "approvals/beanvalidation/QuantityValueExtractor.kt"),
                Approval.KotlinSource.at("test/OrderDateValueExtractor.kt", "approvals/beanvalidation/OrderDateValueExtractor.kt"),
                Approval.JavaSource.at("test/IsbnValueExtractorBase.java", "approvals/beanvalidation/IsbnValueExtractorBase.java"),
                Approval.JavaSource.at("test/IdsProductIdValueExtractorBase.java", "approvals/beanvalidation/IdsProductIdValueExtractorBase.java"),
                Approval.JavaSource.at("test/NullableQuantityValueExtractorBase.java", "approvals/beanvalidation/NullableQuantityValueExtractorBase.java"),
                Approval.JavaSource.at("test/QuantityValueExtractorBase.java", "approvals/beanvalidation/QuantityValueExtractorBase.java"),
                Approval.JavaSource.at("test/OrderDateValueExtractorBase.java", "approvals/beanvalidation/OrderDateValueExtractorBase.java"),
                Approval.ServiceLoader.of("jakarta.validation.valueextraction.ValueExtractor",
                        "test.IdsProductIdValueExtractor",
                        "test.IsbnValueExtractor",
                        "test.NullableQuantityValueExtractor",
                        "test.OrderDateValueExtractor",
                        "test.QuantityValueExtractor")
        ]

        when:
        def result = testkitKotlin.run(projectDir, scenario, approvals)

        then:
        result == Testresult.Kotlin.Approved.of(approvals)
    }

    void "Package override by generator works as expected"() {
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
        testkitKotlin.generatedJavaSourcePath(projectDir, "test/custom/IsbnValueExtractorBase.java").toFile().exists()
        testkitKotlin.generatedKotlinSourcePath(projectDir, "test/custom/IsbnValueExtractor.kt").toFile().exists()
    }
}
