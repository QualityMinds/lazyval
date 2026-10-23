package com.qualityminds.lazyval.processor

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

@Title("Generator Integration - Lettuce Codec")
class ApRedisLettuceCodecIT extends Specification {

    public static final Dependency dependencyLettuce = new Dependency("io.lettuce", "lettuce-core", "7.5.2.RELEASE")

    private static final String GENERATED_FILE_NAME = "LazyvalLettuceCodecs.java"
    private static final String GENERATED_AT_DEFAULT = "test/boundary/persistence/redis/$GENERATED_FILE_NAME"

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    void "Lettuce codecs with combined Scenarios"() {
        given:
        def scenario = Scenario.Java.combined().withDependencies(dependencyLettuce)

        and:
        List<Approval.ForJava> approvals = [
                Approval.JavaSource.at(GENERATED_AT_DEFAULT, "approvals/redis/$GENERATED_FILE_NAME")
        ]

        when:
        def result = testkitJava.run(projectDir, scenario, approvals)

        then:
        result == Testresult.Java.Approved.of(approvals)
    }

    void "Codecs generated at correct default location when no override is given"() {
        given:
        def scenario = Scenario.Java.quantity().withDependencies(dependencyLettuce)

        when:
        testkitJava.run(projectDir, scenario)

        then:
        testkitJava.generatedSourcePath(projectDir, GENERATED_AT_DEFAULT).toFile().exists()
    }

    void "Package override by generator works as expected"() {
        given:
        def scenario = Scenario.Java.quantity().withDependencies(dependencyLettuce)
        scenario.withOption("lazyval.redis.lettuce.package", "test.custom")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)
        testkitJava.generatedSourcePath(projectDir, "test/custom/$GENERATED_FILE_NAME").toFile().exists()
    }

    void "every payload kind of the text table produces codecs that compile"() {
        given: 'booleans, numbers, BigDecimal, UUID, java.time, ZoneId, Date and an enum'
        def scenario = Scenario.Java.ofSingle("scenarios/redis/LettucePayloads.java").withDependencies(dependencyLettuce)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)
    }

    void "a payload without text encoding is skipped with a warning"() {
        given: 'a Locale payload next to a supported one'
        def scenario = Scenario.Java.of("Locale next to Quantity",
                "scenarios/java/Quantity.java", "scenarios/redis/LanguageTag.java")
                .withDependencies(dependencyLettuce)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'the build succeeds and the codecs are generated for the supported type'
        result instanceof Testresult.Java.SuccessWithWarnings
        def success = result as Testresult.Java.SuccessWithWarnings
        success.generatedFiles().contains(GENERATED_FILE_NAME)
        success.warnings().any { it.contains("scenarios.lettuce.LanguageTag") && it.contains("java.util.Locale") }

        and:
        def generated = testkitJava.generatedSourcePath(projectDir, GENERATED_AT_DEFAULT).toFile().text
        generated.contains("QUANTITY")
        !generated.contains("LanguageTag")
    }

    void "nothing is generated when no payload has a text encoding"() {
        given:
        def scenario = Scenario.Java.ofSingle("scenarios/redis/LanguageTag.java").withDependencies(dependencyLettuce)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.SuccessWithWarnings || result instanceof Testresult.Java.NothingGenerated
        !testkitJava.generatedSourcePath(projectDir, GENERATED_AT_DEFAULT).toFile().exists()
    }
}
