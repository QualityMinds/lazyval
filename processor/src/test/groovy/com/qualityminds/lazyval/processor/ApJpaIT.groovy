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

@Title("Generator Integration - JPA")
class ApJpaIT extends Specification {

    public static final Dependency dependencyJakartaPersistence = new Dependency("jakarta.persistence", "jakarta.persistence-api", "3.2.0")

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()


    void "JPA with combined Scenarios"(){
        given: 'a compiler run with all sources'
        def scenario = Scenario.Java.combined().withDependencies(dependencyJakartaPersistence)

        and: 'a defined approval for each generated file'
        List<Approval> approvals = [
                Approval.JavaSource.at("test/boundary/persistence/jpa/QuantityAttributeConverter.java", "approvals/jpa/QuantityAttributeConverter.java"),
                Approval.JavaSource.at("test/boundary/persistence/jpa/IsbnAttributeConverter.java", "approvals/jpa/IsbnAttributeConverter.java"),
                Approval.JavaSource.at("test/boundary/persistence/jpa/OrderDateAttributeConverter.java", "approvals/jpa/OrderDateAttributeConverter.java"),
                Approval.JavaSource.at("test/boundary/persistence/jpa/IdsProductIdAttributeConverter.java", "approvals/jpa/IdsProductIdAttributeConverter.java")
        ]

        when:
        def result = testkitJava.run(projectDir, scenario, approvals)

        then:
        result == Testresult.Java.Approved.of(approvals)
    }

    void "Package override by generator works as expected"(){
        given:
        def scenario = Scenario.Java.quantity().withDependencies(dependencyJakartaPersistence)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.jpa.package", "test.custom")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'file is generated at '
        result == new Testresult.Java.Success("QuantityAttributeConverter.java")

        and: 'file is at correct package'
        testkitJava.generatedSourcePath(projectDir, "test/custom/QuantityAttributeConverter.java").toFile().exists()
    }

}