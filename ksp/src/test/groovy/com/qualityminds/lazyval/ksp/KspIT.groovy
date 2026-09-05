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
    public static final String ERROR_INTERNAL_PROPERTY = "Lazyval: Property 'value' is internal, but the code " +
            "Lazyval generates from it is public \u2014 a mapper, a codec, a converter that reads the payload out " +
            "\u2014 so accepting it would publish the value 'internal' withholds. " +
            "Make the property public, or add a public accessor function and leave the property internal."
    public static final String ERROR_VC_PRIVATE_PROPERTY = "Lazyval: Payload type 'Opaque' is a value " +
            "class whose property 'raw' is not public, so nothing can read the value it wraps. " +
            "Make the property public, or use a different payload type."
    public static final String ERROR_VC_FOREIGN_PROPERTY = "Lazyval: Payload type 'Duration' is a " +
            "value class from outside this compilation whose property 'rawValue' is not public, so " +
            "nothing can read the value it wraps and the declaration cannot be widened from here. " +
            "Use a payload type this project declares, or the plain type Duration converts to."
    public static final String ERROR_VC_NULLABLE_PAYLOAD = "Lazyval: Payload type 'Vague' is a value " +
            "class whose property 'raw' is nullable, and generated code carries that value in place " +
            "of the value class. " +
            "Make the property non-nullable; a value that may be absent belongs to the reference at " +
            "the call site, not inside the value class."
    public static final String ERROR_VC_NULLABLE_FACTORY = "Lazyval: Payload type 'Unsure' is a value " +
            "class whose factory function 'of' returns Unsure?, but generated code has to rebuild a " +
            "non-null Unsure from the payload it carries. " +
            "Make the factory return Unsure, or throw instead of returning null."
    public static final String ERROR_VC_UNCONSTRUCTABLE = "Lazyval: Payload type 'Locked' is a value " +
            "class that can neither be constructed nor built through a factory: its constructor is " +
            "not accessible and it declares no public single-argument factory returning Locked. " +
            "Widen the constructor, or add a factory function in its companion object."
    public static final String ERROR_VC_AMBIGUOUS_FACTORY = "Lazyval: Payload type 'Ambiguous' is a " +
            "value class with multiple matching factory functions, so Lazyval cannot tell which one " +
            "rebuilds it. Please check functions of, from."
    public static final String ERROR_JVM_FIELD_PROPERTY = "Lazyval: Property 'value' is annotated " +
            "@JvmField, which suppresses the getter generated code reads the payload through. " +
            "Remove @JvmField, or add a public accessor function."
    public static final String ERROR_NON_PUBLIC_CONSTRUCTOR = "Lazyval: Constructor " +
            "'IsbnMissingFactory(String)' is private and cannot be called from generated code, " +
            "which is emitted into another package. " +
            "Make the constructor public, or add a factory function in the companion object."
    public static final String ERROR_NON_PUBLIC_FACTORY = "Lazyval: Factory function " +
            "'of(String)' is private and cannot be called from generated code, " +
            "which is emitted into another package. " +
            "Make the factory function public, or add a public constructor."
    public static final String ERROR_INTERNAL_FACTORY = "Lazyval: Factory function 'of(String)' is " +
            "internal, so Kotlin mangles its JVM name with the module name, which generated Java " +
            "cannot depend on. " +
            "Add @JvmName to give it a stable name, or make the function public."
    public static final String ERROR_NON_PUBLIC_TYPE = "Lazyval: Type 'PrivateClass' is private and " +
            "cannot be referenced from generated code, which is emitted into another package. " +
            "Make the type public or internal."
    public static final Dependency dependencyMapstruct = new Dependency("org.mapstruct", "mapstruct", "1.6.3")
    public static final Dependency dependencyJakartaPersistence = new Dependency("jakarta.persistence", "jakarta.persistence-api", "3.2.0")

    @TempDir()
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    void "does not generate anything when classpath is empty"() {
        expect:
        testkitKotlin.run(projectDir, Scenario.Kotlin.isbn()) == new Testresult.Kotlin.NothingGenerated()
    }

    void "does not generate anything when generator is disabled"() {
        given:
        def scenario = Scenario.Kotlin.isbn()
                .withDependencies(dependencyMapstruct)
                .withDisabledGenerators(StockGeneratorIds.MAPSTRUCT)

        expect:
        testkitKotlin.run(projectDir, scenario) == new Testresult.Kotlin.NothingGenerated()
    }

    @Unroll("#scenario.name() fails with '#error'")
    void "Failing Requirement"() {
        given:
        scenario.withDependencies(dependencyMapstruct, dependencyJakartaPersistence)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Failure(error)

        where:
        scenario                                                                     | error
        Scenario.Kotlin.ofSingle("scenarios/failing/AbstractClass.kt")               | "Lazyval: Abstract class is not a valid ValueType."
        Scenario.Kotlin.ofSingle("scenarios/failing/IsbnMissingFactory.kt")          | ERROR_NON_PUBLIC_CONSTRUCTOR
        Scenario.Kotlin.ofSingle("scenarios/failing/PrivateFactoryClass.kt")         | ERROR_NON_PUBLIC_FACTORY
        Scenario.Kotlin.ofSingle("scenarios/failing/InternalFactoryClass.kt")        | ERROR_INTERNAL_FACTORY
        Scenario.Kotlin.ofSingle("scenarios/failing/PrivateClass.kt")                | ERROR_NON_PUBLIC_TYPE
        Scenario.Kotlin.ofSingle("scenarios/failing/ValueClass.kt")                  | "Lazyval: value class is not supported by Lazyval."
        // A value-class payload is supported by unwrapping it, so what is left here are the shapes
        // that cannot be unwrapped and re-wrapped. Each is legal Kotlin, and each names the value
        // class rather than the domain-primitive, because that is where the author has to edit.
        Scenario.Kotlin.ofSingle("scenarios/failing/ValueClassPrivateProperty.kt")   | ERROR_VC_PRIVATE_PROPERTY
        Scenario.Kotlin.ofSingle("scenarios/failing/ValueClassUnconstructable.kt")   | ERROR_VC_UNCONSTRUCTABLE
        Scenario.Kotlin.ofSingle("scenarios/failing/ValueClassAmbiguousFactory.kt")  | ERROR_VC_AMBIGUOUS_FACTORY
        // The last two are unwrappable but refused anyway, because carrying them would break a rule
        // that holds elsewhere: the payload Lazyval carries is what the value class wraps, so a
        // nullable one is the nullable payload NullablePayload above already rejects, and a
        // factory answering null has no caller to hand it to inside the re-wrapping expression.
        Scenario.Kotlin.ofSingle("scenarios/failing/ValueClassNullablePayload.kt")   | ERROR_VC_NULLABLE_PAYLOAD
        Scenario.Kotlin.ofSingle("scenarios/failing/ValueClassNullableFactory.kt")   | ERROR_VC_NULLABLE_FACTORY
        // Same obstacle as ValueClassPrivateProperty in a declaration this compilation cannot widen,
        // so the advice has to differ: kotlin.time.Duration hides rawValue and is not ours to edit.
        Scenario.Kotlin.ofSingle("scenarios/failing/ValueClassForeignProperty.kt")   | ERROR_VC_FOREIGN_PROPERTY
        Scenario.Kotlin.ofSingle("scenarios/failing/MultipleFactoriesClass.kt")      | "Lazyval: Multiple matching factory methods with the same signature found. Please check functions ofNullable, of"
        Scenario.Kotlin.ofSingle("scenarios/failing/MultiplePropertyClass.kt")       | "Lazyval: Not a simple ValueType. Lazyval only supports classes with one non-transient property."
        Scenario.Kotlin.ofSingle("scenarios/failing/MultiplePropertyDataClass.kt")   | "Lazyval: Not a simple ValueType. Lazyval only supports classes with one non-transient property."
        Scenario.Kotlin.ofSingle("scenarios/failing/NullablePayload.kt")         | "Lazyval: Payload must not be nullable. Please use a non-nullable type."
        Scenario.Kotlin.ofSingle("scenarios/failing/ClassWithoutProperty.kt")        | "Lazyval: No accessible properties found. Lazyval requires the ValueType to have exactly one accessible property."
        // Generated code sits in another package, so anything short of public is unreachable. Unlike
        // ClassWithoutProperty above, the property is right there in the source, so the error names it
        // and is reported on the property rather than on the class.
        Scenario.Kotlin.ofSingle("scenarios/failing/PrivatePropertyClass.kt")        | ERROR_PRIVATE_PROPERTY
        Scenario.Kotlin.ofSingle("scenarios/failing/ProtectedPropertyClass.kt")      | ERROR_PROTECTED_PROPERTY
        Scenario.Kotlin.ofSingle("scenarios/failing/InternalPropertyClass.kt")       | ERROR_INTERNAL_PROPERTY
        // Not a visibility problem: @JvmField leaves the property public and removes the getter, so
        // the only way out is an accessor function.
        Scenario.Kotlin.ofSingle("scenarios/failing/JvmFieldPropertyClass.kt")       | ERROR_JVM_FIELD_PROPERTY
        // Deliberately independent of the JVM name: an unmangled internal member is reachable and
        // still refused, because reachability was never the objection for the property. The factory is
        // the opposite case — there the name is the whole objection, and @JvmName settles it.
        Scenario.Kotlin.ofSingle("scenarios/failing/InternalPropertyWithJvmName.kt") | ERROR_INTERNAL_PROPERTY
    }

    @Unroll("#scenario.name() #message")
    void "Edge Cases"() {
        given: 'only Mapstruct Dependency '
        scenario.withDependencies(dependencyMapstruct)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario                                                                          | warning
        Scenario.Kotlin.ofSingle("scenarios/edge/IsbnWithAccessor.kt")                    | null
        Scenario.Kotlin.ofSingle("scenarios/edge/ClassWithTransientProperty.kt")          | null
        Scenario.Kotlin.ofSingle("scenarios/edge/ClassWithPrivateAccessor.kt")            | null
        // Unreachable extra properties must not make the type ambiguous.
        Scenario.Kotlin.ofSingle("scenarios/edge/ClassWithPrivateExtraProperty.kt")       | null
        // The escape hatch the Kotlin docs point at: a non-public property stays valid as long as a
        // public accessor function offers a way in. Keeps that documented advice honest.
        Scenario.Kotlin.ofSingle("scenarios/edge/ClassWithPrivatePropertyAndAccessor.kt") | null
        // The internal twin of the row above, and the supported way to keep a payload internal:
        // writing the accessor makes exposing the value the author's decision rather than Lazyval's.
        Scenario.Kotlin.ofSingle("scenarios/edge/InternalPropertyWithAccessor.kt")        | null
        Scenario.Kotlin.ofSingle("scenarios/edge/InternalConstructorClass.kt")            | null
        // An internal factory, which wants what the constructor above wants and needs @JvmName only
        // because a function has a name. Second row adds the companion routing on top.
        Scenario.Kotlin.ofSingle("scenarios/edge/InternalFactoryWithJvmName.kt")          | null
        Scenario.Kotlin.ofSingle("scenarios/edge/InternalFactoryOnCompanion.kt")          | null
        // A JVM name that parted company with the Kotlin declaration. Success here means javac
        // compiled the mapper, so it is the resolved name that got emitted and not the guessed one.
        Scenario.Kotlin.ofSingle("scenarios/edge/PropertyWithRenamedJvmName.kt")          | null
        Scenario.Kotlin.ofSingle("scenarios/edge/FactoryWithRenamedJvmName.kt")           | null
        // Idiomatic Kotlin factories, reached through the companion field rather than @JvmStatic.
        Scenario.Kotlin.ofSingle("scenarios/edge/FactoryWithoutJvmStatic.kt")             | null
        Scenario.Kotlin.ofSingle("scenarios/edge/FactoryOnNamedCompanion.kt")             | null
        // Kotlin's own naming convention, no annotation needed: an `is`-prefixed Boolean keeps its
        // name as the getter, so a JavaBean spelling would look for a method that is not there.
        Scenario.Kotlin.ofSingle("scenarios/edge/BooleanIsPrefixedProperty.kt")           | null
        Scenario.Kotlin.ofSingle("scenarios/edge/InternalDomainPrimitive.kt")             | null
        // The route for a payload Lazyval refuses to unwrap: pick the unit in the domain-primitive
        // and keep the refused type as a derived view. Pairs with failing/ValueClassForeignProperty.
        Scenario.Kotlin.ofSingle("scenarios/edge/DurationAsNanos.kt")                     | null
        Scenario.Kotlin.ofSingle("scenarios/edge/IsbnNotFinal.kt")                        | "Lazyval: Value Types should not be extendable, hence the class should be final."
        Scenario.Kotlin.ofSingle("scenarios/edge/QuantityMutable.kt")                     | "Lazyval: Value Types should be immutable, hence the payload property should be final (val)."
        expected = warning != null
                ? new Testresult.Kotlin.SuccessWithWarnings(Lists.immutable.of(GENERATED_MAPSTRUCT_MAPPER_NAME), Lists.immutable.of(warning))
                : new Testresult.Kotlin.Success(GENERATED_MAPSTRUCT_MAPPER_NAME)
        message = warning != null
                ? "succeeds with warning '$warning'"
                : "succeeds"
    }

    /**
     * A `value class` payload is unwrapped to the type it wraps, and generated Java reaches it through
     * a generated `internal object` — Kotlin leaves it no accessor Java can name and no constructor
     * Java can call. Success means javac compiled the mapper against that shim, so it really is
     * callable and really does carry the erased type.
     *
     * The shim is listed as a generated file on purpose: it is emitted per domain-primitive that needs
     * one, whether or not any active generator produces Java, so its presence is part of the contract.
     */
    @Unroll("#scenario.name() is unwrapped through #shim")
    void "Value class payload"() {
        given:
        scenario.withDependencies(dependencyMapstruct)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_MAPSTRUCT_MAPPER_NAME, shim)

        where:
        scenario                                                             | shim
        // Public constructor and property on both halves: the plainest route there is.
        Scenario.Kotlin.ofSingle("scenarios/edge/ValueClassPayload.kt")      | "ValueClassPayloadJvmAccess.kt"
        // UInt keeps its property and constructor internal to the stdlib, so this one can only go
        // through the toInt()/toUInt() conversion pair — and it is a classpath declaration besides.
        Scenario.Kotlin.ofSingle("scenarios/edge/UnsignedPayload.kt")        | "UnsignedPayloadJvmAccess.kt"
        // The value class hides its constructor behind a validating factory, which the shim has to use
        // rather than construct around.
        Scenario.Kotlin.ofSingle("scenarios/edge/ValueClassWithFactory.kt")  | "ValueClassWithFactoryJvmAccess.kt"
        // ...and where an init block does the validating, the constructor is the enforcing route, so
        // no factory is needed at all.
        Scenario.Kotlin.ofSingle("scenarios/edge/ValueClassInitBlock.kt")    | "ValueClassInitBlockJvmAccess.kt"
        // Transitive: Outer wraps Inner wraps Long, and the JVM flattens all of it to a long.
        Scenario.Kotlin.ofSingle("scenarios/edge/NestedValueClassPayload.kt")| "NestedValueClassPayloadJvmAccess.kt"
        // The testkit's own predefined scenario, so external SPI implementers inherit a shape that is
        // known to work rather than one that only ever ran here.
        Scenario.Kotlin.valueClassPayload()                                  | "TemperatureJvmAccess.kt"
    }

    void "Warning is issued when package is not configured in any way"() {
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

    void "Error is issued when multiple LazyvalConfigurations are present"() {
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

    void "Error is issued when LazyvalConfiguration marks an local type of the current compilation unit as external"() {
        given:
        def scenario = Scenario.Kotlin.of(
                "package-config-local-type",
                "scenarios/failing/LocalTypeAsExternal.kt",
                "scenarios/failing/LocalTypeAsExternalReferenz.kt")
        expect:
        testkitKotlin.run(projectDir, scenario) == new Testresult.Kotlin.Failure("Lazyval: Type 'scenarios.failing.LocalTypeAsExternalReferenz' listed in @LazyvalConfiguration.externalTypes belongs to the current compilation unit. Annotate it with @LazyValue directly.")
    }
}