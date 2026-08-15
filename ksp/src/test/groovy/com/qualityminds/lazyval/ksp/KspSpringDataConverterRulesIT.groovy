package com.qualityminds.lazyval.ksp

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.*

import java.nio.file.Path

import static com.qualityminds.lazyval.ksp.KspSpringDataStores.*

/**
 * The rules for user-supplied converters (`lazyval.springdata.<store>.converters`).
 *
 * All four stores share one implementation — {@code SpringDataGenerator.validateUserConverters} —
 * so each rule is asserted once here and replayed against every store, rather than copied into the
 * four per-store specs. Those keep only what is genuinely store-specific: the approved output, the
 * supersede behaviour and the package-placement rules.
 *
 * Read {@link Store} first: it is the only thing that varies between iterations.
 */
@Title("KSP Generator Integration - Spring Data User-Converter Rules")
class KspSpringDataConverterRulesIT extends Specification {

    private static final String GENERATED_FILE = "LazyvalSpringDataConfiguration.kt"
    private static final String GENERATED_AT_DEFAULT = "test/boundary/persistence/$GENERATED_FILE"

    @TempDir
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    // ── the rules ────────────────────────────────────────────────────────────────────────────────

    @Unroll("a valid converter is appended to the #store conversions")
    void "valid converters"() {
        given:
        def scenario = scenarioFor(store, "one-valid", "ValidConverter")
        scenario.withOption(store.optionKey, fqn("ValidConverter"))

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE)

        and: 'the converter and the marker comment naming its option are both present'
        generatedAtDefaultLocation().contains("${fqn('ValidConverter')}()")
        generatedAtDefaultLocation().contains("// user-supplied via $store.optionKey:")

        where:
        store << ALL
    }

    @Unroll("whitespace and empty segments in configuration are tolerated for #store")
    void "handling configuration"() {
        given:
        def scenario = scenarioFor(store, "whitespace", "ValidConverter", "AnotherValidConverter")
        scenario.withOption(store.optionKey, " ${fqn('ValidConverter')} , , ${fqn('AnotherValidConverter')} ")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE)

        and:
        generatedAtDefaultLocation().contains("${fqn('ValidConverter')}()")
        generatedAtDefaultLocation().contains("${fqn('AnotherValidConverter')}()")

        where:
        store << ALL
    }

    @Unroll("an internal converter from the current module is accepted for #store")
    void "supports internal converter"() {
        given: 'NonPublicConverter is `internal` and lives in the same KSP-processed module'
        def scenario = scenarioFor(store, "internal-converter", "NonPublicConverter")
        scenario.withOption(store.optionKey, fqn("NonPublicConverter"))

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE)

        and:
        generatedAtDefaultLocation().contains("${fqn('NonPublicConverter')}()")

        where:
        store << ALL
    }

    @Unroll("#store rejects a missing class")
    void "rejects missing class"() {
        given: 'a configured class which is actually not present'
        def scenario = Scenario.Kotlin.quantity().withDependencies(classpathFor(store))
        scenario.withOption(store.optionKey, "com.example.Missing")

        expect: 'fails the compilation'
        failsWith(scenario, store.optionKey, "com.example.Missing", "not found on compile classpath")

        where:
        store << ALL
    }

    @Unroll("#store rejects a class that is not a Converter")
    void "reject class that is not a converter"() {
        given: 'a configured class which is not a Spring-Data converter'
        def scenario = scenarioFor(store, "not-a-converter", "NotAConverter")
        scenario.withOption(store.optionKey, fqn("NotAConverter"))

        expect: 'fails the compilation'
        failsWith(scenario, fqn("NotAConverter"), "does not implement", "Converter")

        where:
        store << ALL
    }

    @Unroll("#store rejects a converter that is unreachable from anywhere")
    void "reject class not visible"() {
        given: 'NonAccessibleConverter is a top-level `private` (file-scoped) class'
        def scenario = scenarioFor(store, "file-private", "NonAccessibleConverter")
        scenario.withOption(store.optionKey, fqn("NonAccessibleConverter"))

        expect:
        failsWith(scenario, fqn("NonAccessibleConverter"), "not accessible")

        where:
        store << ALL
    }

    @Unroll("#store rejects a converter without @ReadingConverter or @WritingConverter")
    void "reject on missing annotations"() {
        given:
        def scenario = scenarioFor(store, "unannotated", "UnannotatedConverter")
        scenario.withOption(store.optionKey, fqn("UnannotatedConverter"))

        expect:
        failsWith(scenario, fqn("UnannotatedConverter"), "@ReadingConverter")

        where:
        store << ALL
    }

    @Unroll("#store rejects a converter without a no-arg constructor")
    void "reject missing no-arg constructor"() {
        given:
        def scenario = scenarioFor(store, "no-noarg", "NoNoArgConverter")
        scenario.withOption(store.optionKey, fqn("NoNoArgConverter"))

        expect:
        failsWith(scenario, fqn("NoNoArgConverter"), "no-arg constructor")

        where:
        store << ALL
    }

    @Unroll("#store reports every invalid converter in one build, not just the first")
    void "reports all invalid"() {
        given:
        def scenario = scenarioFor(store, "two-invalid", "NotAConverter", "NoNoArgConverter")
        scenario.withOption(store.optionKey, "${fqn('NotAConverter')},${fqn('NoNoArgConverter')}")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Failure
        def errors = (result as Testresult.Kotlin.Failure).errors()
        errors.any { it.contains(fqn('NotAConverter')) && it.contains("does not implement") }
        errors.any { it.contains(fqn('NoNoArgConverter')) && it.contains("no-arg constructor") }

        where:
        store << ALL
    }

    @Unroll("the #store option is ignored with a warning when that store is absent")
    void "warn on unsupported option"() {
        given: 'a different store is on the classpath, so no bean method for this one is generated'
        def foil = ALL.find { it != store }
        def scenario = Scenario.Kotlin.quantity().withDependencies(classpathFor(foil))
        scenario.withOption(store.optionKey, "com.example.Whatever")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'the build succeeds — an unused option must never break a build'
        result instanceof Testresult.Kotlin.SuccessWithWarnings
        def success = result as Testresult.Kotlin.SuccessWithWarnings
        success.generatedFiles().contains(GENERATED_FILE)

        and: 'the warning names the option and the store it was meant for'
        success.warnings().any {
            it.contains(store.optionKey) && it.contains(store.label) && it.contains("ignored")
        }

        and:
        !generatedAtDefaultLocation().contains(store.beanMethod)

        where:
        store << ALL
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /** A scenario with the Quantity domain-primitive plus the named converter fixtures. */
    private static def scenarioFor(Store store, String name, String... converters) {
        List<String> sources = ["scenarios/kotlin/Quantity.kt"] +
                converters.collect { "scenarios/converters/${it}.kt" as String }
        Scenario.Kotlin.of("$name-$store", sources as String[]).withDependencies(classpathFor(store))
    }


    private static String fqn(String simpleName) {
        "scenarios.converters.$simpleName"
    }

    private String generatedAtDefaultLocation() {
        testkitKotlin.generatedKotlinSourcePath(projectDir, GENERATED_AT_DEFAULT).toFile().text
    }

    /** Asserts the build fails and that one error mentions every given fragment. */
    private boolean failsWith(scenario, String... fragments) {
        def result = testkitKotlin.run(projectDir, scenario)
        assert result instanceof Testresult.Kotlin.Failure
        assert (result as Testresult.Kotlin.Failure).errors().any { error ->
            fragments.every { error.contains(it) }
        }
        true
    }


}
