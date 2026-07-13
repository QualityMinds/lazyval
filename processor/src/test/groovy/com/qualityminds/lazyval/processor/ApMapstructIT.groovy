package com.qualityminds.lazyval.processor

import com.qualityminds.lazyval.testkit.Approval
import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import org.eclipse.collections.api.factory.Lists
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title

import java.nio.file.Path

@Title("Generator Integration - Mapstruct")
class ApMapstructIT extends Specification {

    public static final Dependency dependencyMapstruct = new Dependency("org.mapstruct", "mapstruct", "1.6.3")
    private static final String GENERATED_FILE_NAME = "LazyvalMapper.java"

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()


    void "Mapstruct with combined Scenarios"() {
        given: 'a compiler run with all sources'
        def scenario = Scenario.Java.combined().withDependencies(dependencyMapstruct)

        and: 'a defined approval for the generated mapper'
        List<Approval.ForJava> approvals = [
                Approval.JavaSource.at("test/$GENERATED_FILE_NAME", "approvals/mapstruct/$GENERATED_FILE_NAME")
        ]

        when:
        def result = testkitJava.run(projectDir, scenario, approvals)

        then:
        result == Testresult.Java.Approved.of(approvals)
    }

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
        testkitJava.generatedSourcePath(projectDir, "test/custom/$GENERATED_FILE_NAME").toFile().exists()
    }
}