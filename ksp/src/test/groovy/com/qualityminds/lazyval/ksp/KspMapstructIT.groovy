package com.qualityminds.lazyval.ksp

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import org.eclipse.collections.api.factory.Lists
import spock.lang.*

import java.nio.file.Path

@Title("KSP Generator Integration - Mapstruct")
class KspMapstructIT extends Specification {

    public static final Dependency dependencyMapstruct = new Dependency("org.mapstruct", "mapstruct", "1.6.3")
    public static final String GENERATED_FILE_NAME = "LazyvalMapper.java"

    @TempDir()
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    @Unroll("#scenario.name() compiles and generated '#expected.generatedFiles()'")
    void "Mapstruct with all Scenarios"(){
        given:
        scenario.withDependencies(dependencyMapstruct)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == expected

        and: 'file is generated at correct location using base-package with generator-default'
        projectDir.resolve("build/generated/ksp/java/test/$GENERATED_FILE_NAME").toFile().exists()

        where:
        scenario << Scenario.Kotlin.all()
        expected = new Testresult.Kotlin.Success(GENERATED_FILE_NAME)
    }

    void "Warning is issued when package is not configured in any way"(){
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(dependencyMapstruct)
        and: 'no base-package nor generator-package is configured '
        scenario.withDisabledBasePackage()

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'warning is issued'
        def expectedWarning = "Lazyval: Neither configuration for 'lazyval.generators.basePackage' nor 'lazyval.mapstruct.package' is set. Falling back to package of first element: 'scenarios.kotlin'"
        result == new Testresult.Kotlin.SuccessWithWarnings(Lists.immutable.of(GENERATED_FILE_NAME), Lists.immutable.of(expectedWarning))
    }

    void "Package override by Generator works as expected"(){
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(dependencyMapstruct)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.mapstruct.package", "test.custom")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME)

        and: 'file is at correct package'
        projectDir.resolve("build/generated/ksp/java/test/custom/$GENERATED_FILE_NAME").toFile().exists()
    }
}