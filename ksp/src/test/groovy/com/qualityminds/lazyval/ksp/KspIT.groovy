package com.qualityminds.lazyval.ksp

import com.qualityminds.lazyval.ksp.spi.StockGeneratorIds
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
    // Spelled out rather than built from a template: reconstructing the message here would let a
    // wrong template pass its own test.
    public static final String ERROR_PRIVATE_PROPERTY = "Lazyval: Property 'value' is private and cannot be read " +
            "from generated code, which is emitted into another package. " +
            "Make the property public, or add a public accessor function."
    public static final String ERROR_PROTECTED_PROPERTY = "Lazyval: Property 'value' is protected and cannot be read " +
            "from generated code, which is emitted into another package. " +
            "Make the property public, or add a public accessor function."
    public static final String ERROR_INTERNAL_PROPERTY = "Lazyval: Property 'value' is internal and cannot be read " +
            "from generated code, which is emitted into another package. Part of that output is Java, which cannot " +
            "call the name-mangled getter Kotlin emits for an internal property. " +
            "Make the property public, or add a public accessor function."
    public static final String ERROR_NON_PUBLIC_CONSTRUCTOR = "Lazyval: Constructor " +
            "'IsbnMissingFactory(String)' is private and cannot be called from generated code, " +
            "which is emitted into another package. " +
            "Make the constructor public, or add a factory function in the companion object."
    public static final String ERROR_NON_PUBLIC_FACTORY = "Lazyval: Factory function " +
            "'of(String)' is private and cannot be called from generated code, " +
            "which is emitted into another package. " +
            "Make the factory function public, or add a public constructor."
    public static final String ERROR_INTERNAL_FACTORY = "Lazyval: Factory function " +
            "'of(String)' is internal and cannot be called from generated code, " +
            "which is emitted into another package. Part of that output is Java, which cannot " +
            "call the name-mangled function Kotlin emits for an internal function. " +
            "Make the factory function public, or add a public constructor."
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
                .withDisabledGenerators(StockGeneratorIds.MAPSTRUCT)

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
        // Reading the payload is only half the contract: the value also has to be reconstructible. This
        // used to assert the kotlinc error that the emitted call provoked downstream — which meant
        // Lazyval was shipping sources it knew would not compile. The rejection belongs at the source.
        Scenario.Kotlin.ofSingle("scenarios/failing/IsbnMissingFactory.kt")       | ERROR_NON_PUBLIC_CONSTRUCTOR
        // A factory only discharges the reconstruction rule if generated code can reach it, so the
        // same package boundary applies to it as to the constructor it stands in for.
        Scenario.Kotlin.ofSingle("scenarios/failing/PrivateFactoryClass.kt")      | ERROR_NON_PUBLIC_FACTORY
        // `internal` is the surprising one for a factory too, and for the same reason it is for a
        // property: the JVM name is mangled, so the Java half of the output cannot call it. An
        // internal *constructor* is not mangled, which is why that one stays accepted.
        Scenario.Kotlin.ofSingle("scenarios/failing/InternalFactoryClass.kt")     | ERROR_INTERNAL_FACTORY
        Scenario.Kotlin.ofSingle("scenarios/failing/ValueClass.kt")               | "Lazyval: value class is not supported by Lazyval."
        Scenario.Kotlin.ofSingle("scenarios/failing/MultipleFactoriesClass.kt")   | "Lazyval: Multiple matching factory methods with the same signature found. Please check functions ofNullable, of"
        Scenario.Kotlin.ofSingle("scenarios/failing/MultiplePropertyClass.kt")    | "Lazyval: Not a simple ValueType. Lazyval only supports classes with one non-transient property."
        Scenario.Kotlin.ofSingle("scenarios/failing/MultiplePropertyDataClass.kt")| "Lazyval: Not a simple ValueType. Lazyval only supports classes with one non-transient property."
        Scenario.Kotlin.ofSingle("scenarios/failing/NullableWrappedType.kt")      | "Lazyval: Wrapped type must not be nullable. Please use a non-nullable type."
        Scenario.Kotlin.ofSingle("scenarios/failing/ClassWithoutProperty.kt")     | "Lazyval: No accessible properties found. Lazyval requires the ValueType to have exactly one accessible property."
        // Generated code sits in another package, so anything short of public is unreachable. Unlike
        // ClassWithoutProperty above, the property is right there in the source, so the error names it
        // and is reported on the property rather than on the class.
        Scenario.Kotlin.ofSingle("scenarios/failing/PrivatePropertyClass.kt")     | ERROR_PRIVATE_PROPERTY
        Scenario.Kotlin.ofSingle("scenarios/failing/ProtectedPropertyClass.kt")   | ERROR_PROTECTED_PROPERTY
        // `internal` is the one that looks like it should work — it is visible to the whole module,
        // generated sources included — so its message explains the name mangling that actually breaks it.
        Scenario.Kotlin.ofSingle("scenarios/failing/InternalPropertyClass.kt")    | ERROR_INTERNAL_PROPERTY
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
        Scenario.Kotlin.ofSingle("scenarios/edge/ClassWithPrivateAccessor.kt")    | null
        // Unreachable extra properties must not make the type ambiguous.
        Scenario.Kotlin.ofSingle("scenarios/edge/ClassWithPrivateExtraProperty.kt")| null
        // The escape hatch the Kotlin docs point at: a non-public property stays valid as long as a
        // public accessor function offers a way in. Keeps that documented advice honest.
        Scenario.Kotlin.ofSingle("scenarios/edge/ClassWithPrivatePropertyAndAccessor.kt")| null
        // The two halves of the `internal` rule that are accepted rather than rejected, kept honest by
        // compiling the output: neither a constructor nor a class name is JVM name-mangled, unlike the
        // property and factory function that KspIT rejects above.
        Scenario.Kotlin.ofSingle("scenarios/edge/InternalConstructorClass.kt")    | null
        Scenario.Kotlin.ofSingle("scenarios/edge/InternalDomainPrimitive.kt")     | null
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

    void "Warning is issued when @LazyvalConfiguration.externalTypes lists the same type twice"() {
        given: 'a Quantity driver to ensure generation runs, plus a config object that lists Year twice'
        def scenario = Scenario.Kotlin.of(
                "package-config-duplicate-external",
                "scenarios/kotlin/Quantity.kt",
                "scenarios/duplicateexternal/Config.kt")
                .withDependencies(dependencyMapstruct)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'the duplicate is reported once; dedup happens at the source so generators see Year only once'
        result instanceof Testresult.Kotlin.SuccessWithWarnings
        ((Testresult.Kotlin.SuccessWithWarnings) result).warnings().contains(
                "Lazyval: Duplicate type 'java.time.Year' in @LazyvalConfiguration.externalTypes. It will only be processed once.")
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