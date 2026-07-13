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

@Title("KSP Generator Integration - JPA")
class KspJpaIT extends Specification {

    public static final Dependency dependencyJakartaAnnotations = new Dependency("jakarta.annotation", "jakarta.annotation-api", "3.0.0")
    public static final Dependency dependencyJakartaPersistence = new Dependency("jakarta.persistence", "jakarta.persistence-api", "3.2.0")

    @TempDir()
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    void "JPA with combined Scenarios"(){
        given: 'a compiler run with all sources'
        def scenario = Scenario.Kotlin.combined().withDependencies(dependencyJakartaPersistence, dependencyJakartaAnnotations)

        and: 'a defined approval for each generated file'
        List<Approval.ForKotlin> approvals = [
                Approval.KotlinSource.at("test/boundary/persistence/jpa/QuantityAttributeConverter.kt", "approvals/jpa/QuantityAttributeConverter.kt"),
                Approval.KotlinSource.at("test/boundary/persistence/jpa/NullableQuantityAttributeConverter.kt", "approvals/jpa/NullableQuantityAttributeConverter.kt"),
                Approval.KotlinSource.at("test/boundary/persistence/jpa/IsbnAttributeConverter.kt", "approvals/jpa/IsbnAttributeConverter.kt"),
                Approval.KotlinSource.at("test/boundary/persistence/jpa/OrderDateAttributeConverter.kt", "approvals/jpa/OrderDateAttributeConverter.kt"),
                Approval.KotlinSource.at("test/boundary/persistence/jpa/IdsProductIdAttributeConverter.kt", "approvals/jpa/IdsProductIdAttributeConverter.kt")
        ]

        when:
        def result = testkitKotlin.run(projectDir, scenario, approvals)

        then:
        result == Testresult.Kotlin.Approved.of(approvals)
    }

    void "Package override by Generator works as expected"(){
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(dependencyJakartaPersistence)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.jpa.package", "test.custom")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Kotlin.Success("QuantityAttributeConverter.kt")

        and: 'file is at correct package'
        testkitKotlin.generatedKotlinSourcePath(projectDir, "test/custom/QuantityAttributeConverter.kt").toFile().exists()
    }

    void "Does not add '@Generated' when jakarta.annotations-api not on classpath"(){
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(dependencyJakartaPersistence)

        and: 'a approval file not containing @Generated'
        def approval = Approval.KotlinSource.at(
                "test/boundary/persistence/jpa/QuantityAttributeConverter.kt",
                "approvals/jpa/QuantityAttributeConverterNoGenerated.kt")

        when:
        def result = testkitKotlin.run(projectDir, scenario, approval)

        then:
        result == Testresult.Kotlin.Approved.of(approval)
    }

}