package com.qualityminds.lazyval.ksp

import com.qualityminds.lazyval.ksp.internal.codegen.MapstructGenerator
import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import org.eclipse.collections.api.factory.Lists
import spock.lang.*

import java.nio.file.Path

@Title("Kotlin Symbol Processor")
class KspIT extends Specification {

    public static final String GENERATED_MAPSTRUCT_MAPPER_NAME = "LazyvalMapper.java"
    public static final Dependency dependencyMapstruct = new Dependency("org.mapstruct", "mapstruct", "1.6.3")
    public static final Dependency dependencyJakartaPersistence = new Dependency("jakarta.persistence", "jakarta.persistence-api", "3.2.0")

    @TempDir()
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    void "does not generate anything when classpath is empty"(){
        expect:
        testkitKotlin.run(projectDir, Scenario.Kotlin.isbn()) == new Testresult.Kotlin.NothingGenerated()
    }

    void "does not generate anything when generator is disabled"(){
        given:
        def scenario = Scenario.Kotlin.isbn()
                .withDependencies(dependencyMapstruct)
                .withDisabledGenerators(MapstructGenerator.GENERATOR_ID)

        expect:
        testkitKotlin.run(projectDir, scenario) == new Testresult.Kotlin.NothingGenerated()
    }

    @Unroll("#scenario.name() fails with '#error'")
    void "Failing Requirement"(){
        given:
        scenario.withDependencies(dependencyMapstruct, dependencyJakartaPersistence)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Failure(error)

        where:
        scenario                                                             | error
        Scenario.Kotlin.ofSingle("scenarios/failing/AbstractClass.kt")            | "Lazyval: Abstract class is not a valid ValueType."
        Scenario.Kotlin.ofSingle("scenarios/failing/IsbnMissingFactory.kt")       | "Cannot access 'constructor(value: String): IsbnMissingFactory': it is private in 'scenarios.failing.IsbnMissingFactory'." // kotlin-compiler warning, so no Lazyval message prefix
        Scenario.Kotlin.ofSingle("scenarios/failing/ValueClass.kt")               | "Lazyval: value class is not supported by Lazyval."
        Scenario.Kotlin.ofSingle("scenarios/failing/MultipleFactoriesClass.kt")   | "Lazyval: Multiple matching factory methods with the same signature found. Please check functions ofNullable, of"
        Scenario.Kotlin.ofSingle("scenarios/failing/MultiplePropertyClass.kt")    | "Lazyval: Not a simple ValueType. Lazyval only supports classes with one non-transient property."
        Scenario.Kotlin.ofSingle("scenarios/failing/MultiplePropertyDataClass.kt")| "Lazyval: Not a simple ValueType. Lazyval only supports classes with one non-transient property."
        Scenario.Kotlin.ofSingle("scenarios/failing/NullableWrappedType.kt")      | "Lazyval: Wrapped type must not be nullable. Please use a non-nullable type."
    }

    @Unroll("#scenario.name() #message")
    void "Edge Cases"(){
        given: 'only Mapstruct Dependency '
        scenario.withDependencies(dependencyMapstruct)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario                                                            | warning
        Scenario.Kotlin.ofSingle("scenarios/edge/IsbnWithAccessor.kt")            | null
        Scenario.Kotlin.ofSingle("scenarios/edge/ClassWithTransientProperty.kt")  | null
        Scenario.Kotlin.ofSingle("scenarios/edge/IsbnNotFinal.kt")                | "Lazyval: Value Types should not be extendable, hence the class should be final."
        Scenario.Kotlin.ofSingle("scenarios/edge/QuantityMutable.kt")             | "Lazyval: Value Types should be immutable, hence the wrapped property should be final (val)."
        expected = warning != null
                ? new Testresult.Kotlin.SuccessWithWarnings(Lists.immutable.of(GENERATED_MAPSTRUCT_MAPPER_NAME), Lists.immutable.of(warning))
                : new Testresult.Kotlin.Success(GENERATED_MAPSTRUCT_MAPPER_NAME)
        message = warning != null
                ? "succeeds with warning '$warning'"
                : "succeeds"
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
        result == new Testresult.Kotlin.SuccessWithWarnings(Lists.immutable.of("LazyvalMapper.java"), Lists.immutable.of(expectedWarning))
    }

    void "Error is issued when multiple LazyvalConfigurations are present" (){
        given:
        def scenario = Scenario.Kotlin.ofSingle("scenarios/failing/MultiConfigs.kt")
        expect:
        testkitKotlin.run(projectDir, scenario) == new Testresult.Kotlin.Failure("Lazyval: Only one @LazyvalConfiguration is allowed per compilation unit.")
    }

    void "Error is issued when LazyvalConfiguration marks an local type of the current compilation unit as external" (){
        given:
        def scenario = Scenario.Kotlin.of(
                "package-config-local-type",
                "scenarios/failing/LocalTypeAsExternal.kt",
                "scenarios/failing/LocalTypeAsExternalReferenz.kt")
        expect:
        testkitKotlin.run(projectDir, scenario) == new Testresult.Kotlin.Failure("Lazyval: Type 'scenarios.failing.LocalTypeAsExternalReferenz' listed in @LazyvalConfiguration.externalTypes belongs to the current compilation unit. Annotate it with @LazyValue directly.")
    }
}