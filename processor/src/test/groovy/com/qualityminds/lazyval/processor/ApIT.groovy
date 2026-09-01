package com.qualityminds.lazyval.processor

import com.qualityminds.lazyval.processor.spi.StockGeneratorIds
import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import org.eclipse.collections.api.factory.Lists
import spock.lang.*

import java.nio.file.Path

@Title("Annotation Processor")
class ApIT extends Specification {

    private static final Dependency dependencyMapstruct = new Dependency("org.mapstruct", "mapstruct", "1.6.3")
    // Only the transient-record-component scenario needs this: without an annotation that javac
    // propagates onto the component, a record has no way to declare derived state at all.
    private static final Dependency dependencyJakartaPersistence = new Dependency("jakarta.persistence", "jakarta.persistence-api", "3.2.0")
    // Spelled out rather than built from a template: reconstructing the message here would let a
    // wrong template pass its own test.
    public static final String ERROR_NO_FIELD = "Lazyval: No non-transient field found. " +
            "Lazyval requires the ValueType to have exactly one non-transient field exposed by a public accessor."
    public static final String ERROR_FIELD_WITHOUT_ACCESSOR = "Lazyval: Field 'value' has no public accessor. " +
            "Lazyval reads the payload through its accessor, which has to be public because generated code " +
            "is emitted into another package. Add a public accessor returning java.lang.String."
    public static final String ERROR_NON_PUBLIC_ACCESSOR = "Lazyval: Accessor 'value()' for field 'value' is " +
            "private and cannot be called from generated code, which is emitted into another package. " +
            "Make the accessor public."
    public static final String ERROR_NON_PUBLIC_CONSTRUCTOR = "Lazyval: Constructor " +
            "'ObjectWithPrivateConstructor(java.lang.String)' is private and cannot be called from generated code, " +
            "which is emitted into another package. " +
            "Make the constructor public, or add a public static factory method."
    public static final String ERROR_NON_PUBLIC_FACTORY = "Lazyval: Factory method " +
            "'of(java.lang.String)' is private and cannot be called from generated code, " +
            "which is emitted into another package. " +
            "Make the factory method public, or add a public constructor."
    public static final String ERROR_NON_PUBLIC_CLASS = "Lazyval: Type 'PackagePrivateObject' is " +
            "package-private and cannot be referenced from generated code, " +
            "which is emitted into another package. Make the type public."
    public static final String ERROR_NON_PUBLIC_RECORD = "Lazyval: Type 'PackagePrivateRecord' is " +
            "package-private and cannot be referenced from generated code, " +
            "which is emitted into another package. Make the type public."
    public static final String ERROR_RECORD_NO_RECONSTRUCTION = "Lazyval: Record " +
            "'RecordTransientWithoutFactory' cannot be reconstructed from its payload alone: the canonical " +
            "constructor also takes the transient component 'derivedLength'. " +
            "Add a constructor taking only java.lang.String, or a public static factory method."

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    void "does not generate anything when classpath is empty"() {
        expect:
        testkitJava.run(projectDir, Scenario.Java.isbn()) == new Testresult.Java.NothingGenerated()
    }

    void "does not generate anything when generator is disabled"() {
        given:
        def scenario = Scenario.Java.isbn()
                .withDependencies(dependencyMapstruct)
                .withDisabledGenerators(StockGeneratorIds.MAPSTRUCT)

        expect:
        testkitJava.run(projectDir, scenario) == new Testresult.Java.NothingGenerated()
    }

    @Unroll("#scenario.name() fails with '#error'")
    void "Failing Requirement"() {
        expect:
        testkitJava.run(projectDir, scenario) == expected

        where:
        scenario                                                                    | error
        Scenario.Java.ofSingle("scenarios/failing/AbstractClass.java")              | "Lazyval: Abstract class is not a valid ValueType."
        Scenario.Java.ofSingle("scenarios/failing/RecordMoreThanOneProperty.java")  | "Lazyval: Not a simple ValueType. Lazyval only supports Records with one non-transient field."
        Scenario.Java.ofSingle("scenarios/failing/ObjectMoreThanOneProperty.java")  | "Lazyval: Not a simple ValueType. Lazyval only supports Objects with one non-transient value."
        Scenario.Java.ofSingle("scenarios/failing/ObjectMultipleFactories.java")    | "Lazyval: Multiple matching factory methods with the same signature found. Please check methods:of, accidental"
        Scenario.Java.ofSingle("scenarios/failing/RecordMultipleFactories.java")    | "Lazyval: Multiple matching factory methods with the same signature found. Please check methods:of, accidental"
        // The field is right there in the source, so the error names it and is reported on the field
        // rather than on the class.
        Scenario.Java.ofSingle("scenarios/failing/ObjectMissingValueAccessor.java") | ERROR_FIELD_WITHOUT_ACCESSOR
        Scenario.Java.ofSingle("scenarios/failing/RecordWithoutProperty.java")      | "Lazyval: No record component found. Lazyval requires the ValueType to have exactly one field."
        // No state at all — nothing to name, so this one stays on the class.
        Scenario.Java.ofSingle("scenarios/failing/ObjectWithoutProperty.java")      | ERROR_NO_FIELD
        Scenario.Java.ofSingle("scenarios/failing/ObjectWithPrivateAccessor.java")  | ERROR_NON_PUBLIC_ACCESSOR
        Scenario.Java.ofSingle("scenarios/failing/ObjectWithPrivateConstructor.java") | ERROR_NON_PUBLIC_CONSTRUCTOR
        Scenario.Java.ofSingle("scenarios/failing/ObjectWithPrivateFactory.java")   | ERROR_NON_PUBLIC_FACTORY
        Scenario.Java.ofSingle("scenarios/failing/PackagePrivateObject.java")
                .withDependencies(dependencyMapstruct)                              | ERROR_NON_PUBLIC_CLASS
        Scenario.Java.ofSingle("scenarios/failing/PackagePrivateRecord.java")
                .withDependencies(dependencyMapstruct)                              | ERROR_NON_PUBLIC_RECORD
        Scenario.Java.ofSingle("scenarios/failing/RecordTransientWithoutFactory.java")
                .withDependencies(dependencyJakartaPersistence)                     | ERROR_RECORD_NO_RECONSTRUCTION
        expected = new Testresult.Java.Failure(error)
    }

    @Unroll("#scenario.name() #message")
    void "Edge Cases"() {
        given: 'only Mapstruct Dependency '
        scenario.withDependencies(dependencyMapstruct)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario                                                               | warning
        Scenario.Java.ofSingle("scenarios/edge/ObjectValueNotFinal.java")      | "Lazyval: Value Types should be immutable, hence the wrapped field should be final."
        Scenario.Java.ofSingle("scenarios/edge/ObjectNotFinal.java")           | "Lazyval: Value Types should not be extendable, hence the class should be final."
        Scenario.Java.ofSingle("scenarios/edge/ObjectWithTransientField.java") | null
        // An unreachable second field must not make the type ambiguous.
        Scenario.Java.ofSingle("scenarios/edge/ObjectWithPrivateExtraField.java") | null
        expected = warning != null
                ? new Testresult.Java.SuccessWithWarnings(Lists.immutable.of("LazyvalMapper.java"), Lists.immutable.of(warning))
                : new Testresult.Java.Success("LazyvalMapper.java")
        message = warning != null
                ? "succeeds with warning '$warning'"
                : "succeeds"
    }

    void "Warning is issued when package is not configured in any way"() {
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

    void "Error is issued when multiple LazyvalConfigurations are present"() {
        given:
        def scenario = Scenario.Java.of(
                "package-config-invalid",
                "scenarios/package-info.java",
                "scenarios/failing/package-info.java")
        expect:
        testkitJava.run(projectDir, scenario) == new Testresult.Java.Failure("Lazyval: Only one @LazyvalConfiguration is allowed per compilation unit.")
    }

    void "Warning is issued when @LazyvalConfiguration.externalTypes lists the same type twice"() {
        given: 'a Quantity driver to ensure generation runs, plus a package-info that lists OptionalInt twice'
        def scenario = Scenario.Java.of(
                "package-config-duplicate-external",
                "scenarios/java/Quantity.java",
                "scenarios/duplicateexternal/package-info.java")
                .withDependencies(dependencyMapstruct)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'the duplicate is reported once; dedup happens at the source so generators see Year only once'
        result instanceof Testresult.Java.SuccessWithWarnings
        ((Testresult.Java.SuccessWithWarnings) result).warnings().contains(
                "Lazyval: Duplicate type 'java.time.Year' in @LazyvalConfiguration.externalTypes. It will only be processed once.")
    }

    void "Error is issued when LazyvalConfiguration marks an local type of the current compilation unit as external"() {
        given:
        def scenario = Scenario.Java.of(
                "package-config-local-type",
                "scenarios/failing/LocalTypeAsExternal.java",
                "scenarios/failing/LocalTypeAsExternalReferenz.java")
        expect:
        testkitJava.run(projectDir, scenario) == new Testresult.Java.Failure("Lazyval: Type 'scenarios.failing.LocalTypeAsExternalReferenz' listed in @LazyvalConfiguration.externalTypes belongs to the current compilation unit. Annotate it with @LazyValue directly.")
    }
}