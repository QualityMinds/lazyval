package com.qualityminds.lazyval.processor

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import org.eclipse.collections.api.factory.Lists
import spock.lang.*

import java.nio.file.Path

@Title("Generator Integration - Mapstruct")
class ApMapstructIT extends Specification {

    public static final Dependency dependencyMapstruct = new Dependency("org.mapstruct", "mapstruct", "1.6.3")
    private static final String GENERATED_FILE_NAME = "LazyvalMapper.java"

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()


    void "Warning is issued when package is not configured in any way"(){
        given:
        def scenario = Scenario.Java.quantity().withDependencies(dependencyMapstruct)
        and: 'no base-package nor generator-package is configured '
        scenario.withDisabledBasePackage()

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'warning is issued'
        def expectedWarning = "Lazyval: Neither configuration for 'lazyval.generators.basePackage' nor 'lazyval.mapstruct.package' is set. Falling back to package of first element: 'scenarios.java'"
        result == new Testresult.Java.SuccessWithWarnings(Lists.immutable.of(GENERATED_FILE_NAME), Lists.immutable.of(expectedWarning))
    }

    void "Package override by generator works as expected"(){
        given:
        def scenario = Scenario.Java.quantity().withDependencies(dependencyMapstruct)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.mapstruct.package", "test.custom")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and: 'file is at correct package'
        projectDir.resolve("build/generated/test/custom/$GENERATED_FILE_NAME").toFile().exists()
    }

    @Unroll("#scenario.name() compiles and generated #expected.generatedFiles()")
    void "Mapstruct with all Scenarios"(){
        given:
        scenario.withDependencies(dependencyMapstruct)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario << Scenario.Java.all()
        expected = new Testresult.Java.Success("LazyvalMapper.java")
    }

}