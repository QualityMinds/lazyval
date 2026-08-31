package com.qualityminds.lazyval.processor

import com.qualityminds.lazyval.processor.internal.codegen.springdata.SpringDataGenerator
import com.qualityminds.lazyval.testkit.Approval
import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.*

import java.nio.file.Path

import static com.qualityminds.lazyval.processor.APSpringDataStores.*

/**
 * Behaviour of the Spring Data generator: when it produces a configuration at all, where that
 * configuration lands, what it looks like per store, and how it supersedes the native codec
 * generators.
 *
 * The rules for user-supplied converters live in {@code ApSpringDataConverterRulesIT}; the stores
 * themselves are declared once in {@link APSpringDataStores}.
 */
@Title("Generator Integration - Spring Data")
class ApSpringDataIT extends Specification {

    private static final String GENERATED_FILE = "LazyvalSpringDataConfiguration.java"
    private static final String GENERATED_AT_DEFAULT = "test/boundary/persistence/$GENERATED_FILE"

    @TempDir
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    // ── per store ────────────────────────────────────────────────────────────────────────────────

    @Unroll("#store with combined Scenarios")
    void "Combined scenarios compiles for all Spring-Data generators"() {
        given: 'a compiler run with all sources and the store specific classpath'
        def scenario = Scenario.Java.combined().withDependencies(classpathFor(store))

        and: 'store-specific approvals'
        List<Approval.ForJava> approvals = [
                Approval.JavaSource.at(GENERATED_AT_DEFAULT, "approvals/springdata/$store.approvalDir/$GENERATED_FILE")
        ]

        when: 'the compiler runs'
        def result = testkitJava.run(projectDir, scenario, approvals)

        then: 'all output matches the given approvals'
        result == Testresult.Java.Approved.of(approvals)

        where:
        store << ALL
    }

    @Unroll("disabling supersede lets the #store codec generator run alongside")
    void "disabled supersede generating additional files"() {
        given: 'the same scenario, but with supersede switched off'
        def scenario = Scenario.Java.combined()
                .withDependencies(classpathFor(store))
                .withOption("lazyval.generators.supersede", "false")

        and: 'the approval still describes Spring-Data-only output'
        List<Approval.ForJava> approvals = [
                Approval.JavaSource.at(GENERATED_AT_DEFAULT, "approvals/springdata/$store.approvalDir/$GENERATED_FILE")
        ]

        when:
        def result = testkitJava.run(projectDir, scenario, approvals)

        then: 'so the codec file shows up as unexpected — proving it is normally superseded'
        result == Testresult.Java.ApprovalMismatch.of([
                new Testresult.Java.ApprovalMismatch.Failure.UnexpectedFile("${store.supersededCodec}.java")
        ])

        where:
        store << WITH_SUPERSEDED_CODEC
    }

    // ── store independent ────────────────────────────────────────────────────────────────────────

    void "nothing is generated when no store module is on the classpath"() {
        given: 'spring-data-commons alone — no CustomConversions type to register with'
        def scenario = Scenario.Java.quantity().withDependencies(baselineOnly())

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.NothingGenerated()
    }

    void "the configuration lands in the default package when no override is given"() {
        given:
        def scenario = Scenario.Java.quantity().withDependencies(classpathFor(CASSANDRA))

        when:
        testkitJava.run(projectDir, scenario)

        then: 'base package from the generator default'
        testkitJava.generatedSourcePath(projectDir, GENERATED_AT_DEFAULT).toFile().exists()
    }

    void "lazyval.springdata.package overrides the target package"() {
        given:
        def scenario = Scenario.Java.quantity().withDependencies(classpathFor(CASSANDRA))
        scenario.withOption("lazyval.springdata.package", "test.custom")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE)

        and:
        testkitJava.generatedSourcePath(projectDir, "test/custom/$GENERATED_FILE").toFile().exists()
    }

    void "every store in the test registry has its option declared by the generator"() {
        given: 'the option keys this spec and the rules spec exercise'
        def expected = ALL*.optionKey

        expect: 'the generator advertises all of them, so none can be silently ignored'
        new SpringDataGenerator().supportedOptions().containsAll(expected)

        and: 'sanity — the registry is not empty, which would make the check vacuous'
        !expected.isEmpty()
    }

    @Unroll("Spring-Data's @Transient on the '#placement' compiles")
    void "Processor excludes derived state from validation when @Transient is present"() {
        given: 'a value type whose second, derived value carries the annotation'
        def scenario = Scenario.Java.ofSingle(source).withDependencies(classpathFor(CASSANDRA))

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'the type is accepted and the configuration is generated for the remaining value'
        result == new Testresult.Java.Success(GENERATED_FILE)

        where:
        placement | source
        "field"   | "scenarios/springdata/SpringDataTransientField.java"
        "getter"  | "scenarios/springdata/SpringDataTransientGetter.java"
    }

    void "a domain-primitive wrapping LocalDate generates valid converters"() {
        given:
        def scenario = Scenario.Java.orderDate().withDependencies(classpathFor(CASSANDRA))

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE)
    }
}
