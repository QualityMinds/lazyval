package com.qualityminds.lazyval.ksp

import com.qualityminds.lazyval.testkit.Approval
import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title

import java.nio.file.Path

@Title("KSP Generator Integration - Lettuce Codec")
class KspRedisLettuceCodecIT extends Specification {

    public static final Dependency dependencyJakartaAnnotations = new Dependency("jakarta.annotation", "jakarta.annotation-api", "3.0.0")
    public static final Dependency dependencyLettuce = new Dependency("io.lettuce", "lettuce-core", "7.5.2.RELEASE")

    private static final String GENERATED_FILE_NAME = "LazyvalLettuceCodecs.kt"
    private static final String GENERATED_AT_DEFAULT = "test/boundary/persistence/redis/$GENERATED_FILE_NAME"

    @TempDir()
    Path projectDir
    @Shared
    def testkitKotlin = Testkit.kotlin()

    void "Lettuce codecs with combined Scenarios"() {
        given:
        def scenario = Scenario.Kotlin.combined().withDependencies(dependencyLettuce, dependencyJakartaAnnotations)

        and:
        List<Approval.ForKotlin> approvals = [
                Approval.KotlinSource.at(GENERATED_AT_DEFAULT, "approvals/redis/$GENERATED_FILE_NAME")
        ]

        when:
        def result = testkitKotlin.run(projectDir, scenario, approvals)

        then:
        result == Testresult.Kotlin.Approved.of(approvals)
    }

    void "Codecs generated at correct default location when no override is given"() {
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(dependencyLettuce)

        when:
        testkitKotlin.run(projectDir, scenario)

        then:
        testkitKotlin.generatedKotlinSourcePath(projectDir, GENERATED_AT_DEFAULT).toFile().exists()
    }

    void "Package override by generator works as expected"() {
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(dependencyLettuce)
        scenario.withOption("lazyval.redis.lettuce.package", "test.custom")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME)
        testkitKotlin.generatedKotlinSourcePath(projectDir, "test/custom/$GENERATED_FILE_NAME").toFile().exists()
    }

    void "every payload kind of the text table produces codecs that compile"() {
        given: 'booleans, numbers, BigDecimal, UUID, java.time, ZoneId, Date and an enum'
        def scenario = Scenario.Kotlin.ofSingle("scenarios/redis/LettucePayloads.kt").withDependencies(dependencyLettuce)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME)
    }

    void "a value class payload is encoded through its unwrapped type"() {
        given:
        def scenario = Scenario.Kotlin.valueClassPayload().withDependencies(dependencyLettuce)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Success
        (result as Testresult.Kotlin.Success).generatedFiles().contains(GENERATED_FILE_NAME)
    }

    void "a payload without text encoding is skipped with a warning"() {
        given: 'a Locale payload next to a supported one'
        def scenario = Scenario.Kotlin.of("Locale next to Quantity",
                "scenarios/kotlin/Quantity.kt", "scenarios/redis/LanguageTag.kt")
                .withDependencies(dependencyLettuce)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'the build succeeds and the codecs are generated for the supported type'
        result instanceof Testresult.Kotlin.SuccessWithWarnings
        def success = result as Testresult.Kotlin.SuccessWithWarnings
        success.generatedFiles().contains(GENERATED_FILE_NAME)
        success.warnings().any { it.contains("scenarios.lettuce.LanguageTag") && it.contains("java.util.Locale") }

        and:
        def generated = testkitKotlin.generatedKotlinSourcePath(projectDir, GENERATED_AT_DEFAULT).toFile().text
        generated.contains("QUANTITY")
        !generated.contains("LanguageTag")
    }

    void "nothing is generated when no payload has a text encoding"() {
        given:
        def scenario = Scenario.Kotlin.ofSingle("scenarios/redis/LanguageTag.kt").withDependencies(dependencyLettuce)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.SuccessWithWarnings || result instanceof Testresult.Kotlin.NothingGenerated
        !testkitKotlin.generatedKotlinSourcePath(projectDir, GENERATED_AT_DEFAULT).toFile().exists()
    }
}
