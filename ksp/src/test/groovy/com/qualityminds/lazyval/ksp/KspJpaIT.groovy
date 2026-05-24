package com.qualityminds.lazyval.ksp

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import org.eclipse.collections.api.factory.Lists
import spock.lang.*

import java.nio.file.Path

@Title("KSP Generator Integration - JPA")
class KspJpaIT extends Specification {

    public static final Dependency dependencyJakartaPersistence = new Dependency("jakarta.persistence", "jakarta.persistence-api", "3.2.0")

    @TempDir()
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    @Unroll("#scenario.name() compiles and generated #expected.generatedFiles()")
    void "JPA with all Scenarios"(){
        given:
        scenario.withDependencies(dependencyJakartaPersistence)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario << Scenario.Kotlin.all()
        generatedJpaMapper = scenario.name().replace(".kt", "AttributeConverter.kt")
        expected = new Testresult.Kotlin.Success(generatedJpaMapper)
    }

    void "JPA generated at correct default location when no override is given"(){
        given:
        def scenario = Scenario.Kotlin.quantity()
                .withDependencies(dependencyJakartaPersistence)

        when:
        testkitKotlin.run(projectDir, scenario)

        then: 'file is generated at correct location using base-package with generator-default'
        projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/jpa/QuantityAttributeConverter.kt").toFile().exists()
    }


    void "Package override by Generator works as expected"(){
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(dependencyJakartaPersistence)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.jpa.package", "test.custom")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Kotlin.Success(Lists.immutable.of("QuantityAttributeConverter.kt"))

        and: 'file is at correct package'
        projectDir.resolve("build/generated/ksp/kotlin/test/custom/QuantityAttributeConverter.kt").toFile().exists()
    }

}