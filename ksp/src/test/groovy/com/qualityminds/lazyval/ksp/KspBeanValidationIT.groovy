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
                Approval.KotlinSource.at("test/domain/IsbnValueExtractor.kt", "approvals/beanvalidation/IsbnValueExtractor.kt"),
                Approval.KotlinSource.at("test/domain/IdsProductIdValueExtractor.kt", "approvals/beanvalidation/IdsProductIdValueExtractor.kt"),
                Approval.KotlinSource.at("test/domain/NullableQuantityValueExtractor.kt", "approvals/beanvalidation/NullableQuantityValueExtractor.kt"),
                Approval.KotlinSource.at("test/domain/QuantityValueExtractor.kt", "approvals/beanvalidation/QuantityValueExtractor.kt"),
                Approval.KotlinSource.at("test/domain/OrderDateValueExtractor.kt", "approvals/beanvalidation/OrderDateValueExtractor.kt"),
                Approval.JavaSource.at("test/domain/IsbnValueExtractorBase.java", "approvals/beanvalidation/IsbnValueExtractorBase.java"),
                Approval.JavaSource.at("test/domain/IdsProductIdValueExtractorBase.java", "approvals/beanvalidation/IdsProductIdValueExtractorBase.java"),
                Approval.JavaSource.at("test/domain/NullableQuantityValueExtractorBase.java", "approvals/beanvalidation/NullableQuantityValueExtractorBase.java"),
                Approval.JavaSource.at("test/domain/QuantityValueExtractorBase.java", "approvals/beanvalidation/QuantityValueExtractorBase.java"),
                Approval.JavaSource.at("test/domain/OrderDateValueExtractorBase.java", "approvals/beanvalidation/OrderDateValueExtractorBase.java"),
                Approval.ServiceLoader.of("jakarta.validation.valueextraction.ValueExtractor",
                        "test.domain.IdsProductIdValueExtractor",
                        "test.domain.IsbnValueExtractor",
                        "test.domain.NullableQuantityValueExtractor",
                        "test.domain.OrderDateValueExtractor",
                        "test.domain.QuantityValueExtractor")
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
