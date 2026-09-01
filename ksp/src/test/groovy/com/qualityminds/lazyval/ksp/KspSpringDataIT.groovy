package com.qualityminds.lazyval.ksp

import com.qualityminds.lazyval.ksp.internal.codegen.springdata.SpringDataGenerator
import com.qualityminds.lazyval.testkit.Approval
import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.*

import java.nio.file.Files
import java.nio.file.Path

import static com.qualityminds.lazyval.ksp.KspSpringDataStores.*

/**
 * Behaviour of the Spring Data generator under KSP: when it produces a configuration at all, where
 * that configuration lands, what it looks like per store, how it supersedes the native codec
 * generators, and whether the `@Generated` stamp is optional.
 *
 * The rules for user-supplied converters live in {@code KspSpringDataConverterRulesIT}; the stores
 * themselves are declared once in {@link KspSpringDataStores}.
 */
@Title("KSP Generator Integration - Spring Data")
class KspSpringDataIT extends Specification {

    private static final String GENERATED_FILE = "LazyvalSpringDataConfiguration.kt"
    private static final String GENERATED_AT_DEFAULT = "test/boundary/persistence/$GENERATED_FILE"

    @TempDir
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    // ── per store ────────────────────────────────────────────────────────────────────────────────

    @Unroll("#store with combined Scenarios")
    void "Combined scenarios compiles for all Spring-Data generators"() {
        given: 'a compiler run with all sources and the store specific classpath'
        def scenario = Scenario.Kotlin.combined().withDependencies(classpathFor(store))

        and: 'store-specific approvals'
        List<Approval.ForKotlin> approvals = [
                Approval.KotlinSource.at(GENERATED_AT_DEFAULT, "approvals/springdata/$store.approvalDir/$GENERATED_FILE")
        ]

        when: 'the compiler runs'
        def result = testkitKotlin.run(projectDir, scenario, approvals)

        then:
        result == Testresult.Kotlin.Approved.of(approvals)

        where: 'all output matches the given approvals'
        store << ALL
    }

    @Unroll("disabling supersede lets the #store codec generator run alongside")
    void "disabled supersede generating additional files"() {
        given: 'the same scenario, but with supersede switched off'
        def scenario = Scenario.Kotlin.combined()
                .withDependencies(classpathFor(store))
                .withOption("lazyval.generators.supersede", "false")

        and: 'the approval still describes Spring-Data-only output'
        List<Approval.ForKotlin> approvals = [
                Approval.KotlinSource.at(GENERATED_AT_DEFAULT, "approvals/springdata/$store.approvalDir/$GENERATED_FILE")
        ]

        when:
        def result = testkitKotlin.run(projectDir, scenario, approvals)

        then: 'so the codec file shows up as unexpected — proving it is normally superseded'
        result == Testresult.Kotlin.ApprovalMismatch.of([
                new Testresult.Kotlin.ApprovalMismatch.Failure.UnexpectedFile("${store.supersededCodec}.kt")
        ])

        where:
        store << WITH_SUPERSEDED_CODEC
    }

    // ── store independent ────────────────────────────────────────────────────────────────────────

    void "nothing is generated when no store module is on the classpath"() {
        given: 'spring-data-commons alone — no CustomConversions type to register with'
        def scenario = Scenario.Kotlin.quantity().withDependencies(baselineOnly())

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.NothingGenerated()
    }

    void "the configuration lands in the default package when no override is given"() {
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(classpathFor(CASSANDRA))

        when:
        testkitKotlin.run(projectDir, scenario)

        then: 'base package from the generator default'
        testkitKotlin.generatedKotlinSourcePath(projectDir, GENERATED_AT_DEFAULT).toFile().exists()
    }

    void "lazyval.springdata.package overrides the target package"() {
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(classpathFor(CASSANDRA))
        scenario.withOption("lazyval.springdata.package", "test.custom")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE)

        and:
        testkitKotlin.generatedKotlinSourcePath(projectDir, "test/custom/$GENERATED_FILE").toFile().exists()
    }

    @Unroll("Spring-Data's @Transient on the '#placement' compiles")
    void "Processor excludes derived state from validation when @Transient is present"() {
        given: 'a value type whose second, derived property carries the annotation'
        def scenario = Scenario.Kotlin.ofSingle(source).withDependencies(classpathFor(CASSANDRA))

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'the type is accepted and the configuration is generated for the remaining property'
        result == new Testresult.Kotlin.Success(GENERATED_FILE)

        where:
        placement               | source
        "field"                 | "scenarios/springdata/SpringDataTransientProperty.kt"
        "getter"                | "scenarios/springdata/SpringDataTransientGetter.kt"
        "constructor property"  | "scenarios/springdata/SpringDataTransientConstructorProperty.kt"
    }

    void "every store in the test registry has its option declared by the generator"() {
        given: 'the option keys this spec and the rules spec exercise'
        def expected = ALL*.optionKey

        expect: 'the generator advertises all of them, so none can be silently ignored'
        new SpringDataGenerator().supportedOptions().containsAll(expected)

        and: 'sanity — the registry is not empty, which would make the check vacuous'
        !expected.isEmpty()
    }

    /**
     * The `@Generated` stamp is optional for every generator: jakarta.annotation-api is not something
     * a consumer must depend on, so the annotation is emitted only when it is resolvable. Asserted
     * here because the Spring Data generator is the one with the widest classpath matrix, but the
     * behaviour comes from the shared {@code GeneratedStamp}.
     */
    void "the @Generated stamp is omitted when jakarta.annotation-api is absent"() {
        given:
        def scenario = Scenario.Kotlin.quantity()
                .withDependencies(classpathWithoutAnnotationApi(CASSANDRA))

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE)

        and:
        !Files.readString(testkitKotlin.generatedKotlinSourcePath(projectDir, GENERATED_AT_DEFAULT))
                .contains("@Generated")
    }
}
