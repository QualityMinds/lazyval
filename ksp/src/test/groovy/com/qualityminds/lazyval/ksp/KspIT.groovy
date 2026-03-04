package com.qualityminds.lazyval.ksp

import com.qualityminds.lazyval.ksp.codegen.MapstructGenerator
import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import org.eclipse.collections.api.factory.Lists
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.file.Path

class KspIT extends Specification {

    public static final String GENERATED_MAPSTRUCT_MAPPER_NAME = "LazyvalMapper.java"
    public static final Dependency DependencyMapstruct = new Dependency("org.mapstruct", "mapstruct", "1.6.3")
    public static final Dependency DependencyJakartaPersistence = new Dependency("jakarta.persistence", "jakarta.persistence-api", "3.2.0")

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
                .withDependencies(DependencyMapstruct)
                .withDisabledGenerators(MapstructGenerator.GENERATOR_ID)

        expect:
        testkitKotlin.run(projectDir, scenario) == new Testresult.Kotlin.NothingGenerated()
    }

    @Unroll("#scenario.name() fails with '#error'")
    void "Failing Requirement"(){
        given:
        scenario.withDependencies(DependencyMapstruct, DependencyJakartaPersistence)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Failure(error)

        where:
        scenario                                                             | error
        Scenario.Kotlin.of( "scenarios/failing/AbstractClass.kt")            | "Abstract class is not a valid ValueType."
        Scenario.Kotlin.of( "scenarios/failing/IsbnMissingFactory.kt")       | "Cannot access 'constructor(value: String): IsbnMissingFactory': it is private in 'scenarios.failing.IsbnMissingFactory'."
        Scenario.Kotlin.of( "scenarios/failing/ValueClass.kt")               | "value class is not supported by Lazyval."
        Scenario.Kotlin.of( "scenarios/failing/MultipleFactoriesClass.kt")   | "Multiple matching factory methods with the same signature found. Please check functions ofNullable, of"
        Scenario.Kotlin.of( "scenarios/failing/MultiplePropertyClass.kt")    | "Not a simple ValueType. Lazyval only supports classes with one property."
        Scenario.Kotlin.of( "scenarios/failing/MultiplePropertyDataClass.kt")| "Not a simple ValueType. Lazyval only supports classes with one property."
        Scenario.Kotlin.of( "scenarios/failing/NullableWrappedType.kt")      | "Wrapped type must not be nullable. Please use a non-nullable type."
    }

    @Unroll("#scenario.name() #message")
    void "Edge Cases"(){
        given: 'only Mapstruct Dependency '
        scenario.withDependencies(DependencyMapstruct)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario                                                     | warning
        Scenario.Kotlin.of("scenarios/edge/IsbnWithAccessor.kt")     | null
        Scenario.Kotlin.of("scenarios/edge/IsbnNotFinal.kt")         | "Value Types should not be extendable, hence the class should be final."
        Scenario.Kotlin.of("scenarios/edge/QuantityMutable.kt")      | "Value Types should be immutable, hence the wrapped property should be final (val)."
        expected = warning != null
                ? new Testresult.Kotlin.SuccessWithWarnings(Lists.immutable.of(GENERATED_MAPSTRUCT_MAPPER_NAME), Lists.immutable.of(warning))
                : new Testresult.Kotlin.Success(GENERATED_MAPSTRUCT_MAPPER_NAME)
        message = warning != null
                ? "succeeds with warning '$warning'"
                : "succeeds"
    }

    @Unroll("#scenario.name() compiles and generated '#expected.generatedFiles()'")
    void "Successful Generation"(){
        given:
        scenario.withDependencies(DependencyMapstruct, DependencyJakartaPersistence)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario                              | generatedJpaMapper
        Scenario.Kotlin.isbn() | "IsbnAttributeConverter.kt"
        Scenario.Kotlin.quantity() | "QuantityAttributeConverter.kt"
        Scenario.Kotlin.nullableQuantity() | "NullableQuantityAttributeConverter.kt"
        Scenario.Kotlin.productId() | "ProductIdAttributeConverter.kt"
        expected = new Testresult.Kotlin.Success(GENERATED_MAPSTRUCT_MAPPER_NAME, generatedJpaMapper)
    }
}