package de.qualityminds.lazyval.processor

import de.qualityminds.lazyval.testkit.Testkit
import de.qualityminds.lazyval.testkit.Testresult
import de.qualityminds.lazyval.testkit.dependencies.Dependency
import de.qualityminds.lazyval.testkit.scenarios.Scenario
import org.eclipse.collections.api.factory.Lists
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.file.Path

class AnnotationProcessorIT extends Specification {

    public static final String GENERATED_MAPSTRUCT_MAPPER_NAME = "LazyvalMapper.java"
    public static final Dependency DependencyMapstruct = new Dependency("org.mapstruct", "mapstruct", "1.6.3")
    public static final Dependency DependencyJakartaPersistence = new Dependency("jakarta.persistence", "jakarta.persistence-api", "3.2.0")

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    void "does not generate anything when classpath is empty"(){
        expect:
        testkitJava.run(projectDir, Scenario.Java.Isbn) == new Testresult.Java.NothingGenerated()
    }

    @Unroll("#scenario.name() fails with '#error'")
    void "Failing Requirement"(){
        expect:
        testkitJava.run(projectDir, scenario) == expected

        where:
        scenario | error
        Scenario.Java.of( "scenarios/failing/AbstractClass.java")               | "Abstract class is not a valid ValueType."
        Scenario.Java.of( "scenarios/failing/RecordMoreThanOneProperty.java")   | "Not a simple ValueType. Lazyval only supported Records with one non-transient field name 'value'."
        Scenario.Java.of( "scenarios/failing/ObjectMoreThanOneProperty.java")   | "Not a simple ValueType. Lazyval only supports Objects with one non-transient value."
        Scenario.Java.of( "scenarios/failing/ObjectMultipleFactories.java")     | "Multiple matching factory methods with the same signature found. Please check methods:of, accidental"
        Scenario.Java.of( "scenarios/failing/RecordMultipleFactories.java")     | "Multiple matching factory methods with the same signature found. Please check methods:of, accidental"
        Scenario.Java.of( "scenarios/failing/ObjectMissingValueAccessor.java")  | "No public accessor found. Lazyval requires the ValueType to have one accessor. Stopping further validation."
        expected = new Testresult.Java.Failure(Lists.immutable.of(error))
    }

    @Unroll("#scenario.name() #message")
    void "Edge Cases"(){
        given: 'only Mapstruct Dependency '
        scenario.withDependencies(DependencyMapstruct)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario                                                    | warning
        Scenario.Java.of("scenarios/edge/ObjectValueNotFinal.java") | "Value Types should be immutable, hence the wrapped field should be final."
        Scenario.Java.of("scenarios/edge/ObjectNotFinal.java")      | "Value Types should not be extendable, hence the class should be final."
        expected = warning != null
                ? new Testresult.Java.SuccessWithWarnings(Lists.immutable.of(GENERATED_MAPSTRUCT_MAPPER_NAME), Lists.immutable.of(warning))
                : new Testresult.Java.Success(GENERATED_MAPSTRUCT_MAPPER_NAME)
        message = warning != null
                ? "succeeds with warning '$warning'"
                : "succeeds"
    }

    @Unroll("#scenario.name() compiles and generated #expected.generatedFiles()")
    void "Successful Generation"(){
        given:
        scenario.withDependencies(DependencyMapstruct, DependencyJakartaPersistence)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario                | generatedJpaMapper
        Scenario.Java.Isbn      | "IsbnAttributeConverter.java"
        Scenario.Java.Quantity  | "QuantityAttributeConverter.java"
        Scenario.Java.ProductId | "ProductIdAttributeConverter.java"
        expected = new Testresult.Java.Success(GENERATED_MAPSTRUCT_MAPPER_NAME, generatedJpaMapper)
    }
}