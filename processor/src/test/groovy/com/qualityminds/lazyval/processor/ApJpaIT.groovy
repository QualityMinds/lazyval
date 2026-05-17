package com.qualityminds.lazyval.processor

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import org.eclipse.collections.api.factory.Lists
import spock.lang.*

import java.nio.file.Path

@Title("Generator Integration - JPA")
class ApJpaIT extends Specification {

    public static final Dependency dependencyJakartaPersistence = new Dependency("jakarta.persistence", "jakarta.persistence-api", "3.2.0")

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()


    @Unroll("#scenario.name() compiles and generated #expected.generatedFiles()")
    void "JPA with all Scenarios"(){
        given:
        scenario.withDependencies(dependencyJakartaPersistence)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario << Scenario.Java.all()
        generatedJpaMapper = scenario.name().replace(".java", "AttributeConverter.java")
        expected = new Testresult.Java.Success(generatedJpaMapper)
    }

    void "AttributeConverter generated at correct default location when no override is given"(){
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencyJakartaPersistence)

        when:
        testkitJava.run(projectDir, scenario)

        then: 'file is generated at correct location using base-package with generator-default'
        projectDir.resolve("build/generated/test/boundary/persistence/jpa/QuantityAttributeConverter.java").toFile().exists()
    }

    void "Package override by generator works as expected"(){
        given:
        def scenario = Scenario.Java.quantity().withDependencies(dependencyJakartaPersistence)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.jpa.package", "test.custom")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Java.Success(Lists.immutable.of("QuantityAttributeConverter.java"))

        and: 'file is at correct package'
        projectDir.resolve("build/generated/test/custom/QuantityAttributeConverter.java").toFile().exists()
    }

}