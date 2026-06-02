package com.qualityminds.lazyval.processor

import com.qualityminds.lazyval.processor.internal.codegen.MapstructGenerator
import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import org.eclipse.collections.api.factory.Lists
import spock.lang.*

import java.nio.file.Path

@Title("Annotation Processor")
class ApIT extends Specification {

    public static final Dependency dependencyMapstruct = new Dependency("org.mapstruct", "mapstruct", "1.6.3")
    public static final Dependency dependencyJakartaPersistence = new Dependency("jakarta.persistence", "jakarta.persistence-api", "3.2.0")

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    void "does not generate anything when classpath is empty"(){
        expect:
        testkitJava.run(projectDir, Scenario.Java.isbn()) == new Testresult.Java.NothingGenerated()
    }

    void "does not generate anything when generator is disabled"(){
        given:
        def scenario = Scenario.Java.isbn()
                .withDependencies(dependencyMapstruct)
                .withDisabledGenerators(MapstructGenerator.GENERATOR_ID)

        expect:
        testkitJava.run(projectDir, scenario) == new Testresult.Java.NothingGenerated()
    }

    @Unroll("#scenario.name() fails with '#error'")
    void "Failing Requirement"(){
        expect:
        testkitJava.run(projectDir, scenario) == expected

        where:
        scenario                                                                | error
        Scenario.Java.of( "scenarios/failing/AbstractClass.java")               | "Lazyval: Abstract class is not a valid ValueType."
        Scenario.Java.of( "scenarios/failing/RecordMoreThanOneProperty.java")   | "Lazyval: Not a simple ValueType. Lazyval only supports Records with one non-transient field."
        Scenario.Java.of( "scenarios/failing/ObjectMoreThanOneProperty.java")   | "Lazyval: Not a simple ValueType. Lazyval only supports Objects with one non-transient value."
        Scenario.Java.of( "scenarios/failing/ObjectMultipleFactories.java")     | "Lazyval: Multiple matching factory methods with the same signature found. Please check methods:of, accidental"
        Scenario.Java.of( "scenarios/failing/RecordMultipleFactories.java")     | "Lazyval: Multiple matching factory methods with the same signature found. Please check methods:of, accidental"
        Scenario.Java.of( "scenarios/failing/ObjectMissingValueAccessor.java")  | "Lazyval: No public accessor found. Lazyval requires the ValueType to have one accessor. Stopping further validation."
        expected = new Testresult.Java.Failure(error)
    }

    @Unroll("#scenario.name() #message")
    void "Edge Cases"(){
        given: 'only Mapstruct Dependency '
        scenario.withDependencies(dependencyMapstruct)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario                                                            | warning
        Scenario.Java.of("scenarios/edge/ObjectValueNotFinal.java")         | "Lazyval: Value Types should be immutable, hence the wrapped field should be final."
        Scenario.Java.of("scenarios/edge/ObjectNotFinal.java")              | "Lazyval: Value Types should not be extendable, hence the class should be final."
        Scenario.Java.of("scenarios/edge/ObjectWithTransientField.java")    | null
        expected = warning != null
                ? new Testresult.Java.SuccessWithWarnings(Lists.immutable.of("LazyvalMapper.java"), Lists.immutable.of(warning))
                : new Testresult.Java.Success("LazyvalMapper.java")
        message = warning != null
                ? "succeeds with warning '$warning'"
                : "succeeds"
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
        result == new Testresult.Java.SuccessWithWarnings(Lists.immutable.of("LazyvalMapper.java"), Lists.immutable.of(expectedWarning))
    }

    void "Error is issued when multiple LazyvalConfigurations are present" (){
        given:
        def scenario = Scenario.Java.of("scenarios/package-info.java", "scenarios/failing/package-info.java")
        expect:
        testkitJava.run(projectDir, scenario) == new Testresult.Java.Failure("Lazyval: Only one @LazyvalConfiguration is allowed per compilation unit.")
    }

    void "Error is issued when LazyvalConfiguration marks an local type of the current compilation unit as external" (){
        given:
        def scenario = Scenario.Java.of("scenarios/failing/LocalTypeAsExternal.java", "scenarios/failing/LocalTypeAsExternalReferenz.java")
        expect:
        testkitJava.run(projectDir, scenario) == new Testresult.Java.Failure("Lazyval: Type 'scenarios.failing.LocalTypeAsExternalReferenz' listed in @LazyvalConfiguration.externalTypes belongs to the current compilation unit. Annotate it with @LazyValue directly.")
    }
}