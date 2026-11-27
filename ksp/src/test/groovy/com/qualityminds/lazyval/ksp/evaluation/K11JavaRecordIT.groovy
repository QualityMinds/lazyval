package com.qualityminds.lazyval.ksp.evaluation

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title

import java.nio.file.Path

/**
 * AI evaluation finding K11 (GitHub issue "Evaluation: ksp", label evaluation). This specification intentionally
 * fails on main: it documents the expected behaviour, not the current one.
 * <p>
 * A Java record is the plainest Java domain-primitive there is, and the APT side accepts it. On the KSP side its
 * component is not found as a property, so both a record from a library listed in {@code externalTypes} and a
 * record compiled with the module fail with "No accessible properties found". For the library record the error is
 * reported at a {@code NonExistLocation}, so nothing tells the user which type it is about.
 */
@Title("Evaluation K11 - Java records")
class K11JavaRecordIT extends Specification {

    static final Dependency JPA = new Dependency("jakarta.persistence", "jakarta.persistence-api", "3.2.0")

    @TempDir
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    void "K11 a Java record from a library listed in externalTypes is a domain-primitive"() {
        given:
        def scenario = Scenario.Kotlin.ofSingle("scenarios/evaluation/k11/MetersConfig.kt")
                .withJavaLibrary("scenarios/evaluation/k11/Meters.java")
                .withDependencies(JPA)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success("MetersAttributeConverter.kt")
    }

    void "K11 a Java record annotated with @LazyValue in the module is a domain-primitive"() {
        given:
        def scenario = Scenario.Kotlin.ofSingle("scenarios/evaluation/k11/Seconds.java").withDependencies(JPA)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success("SecondsAttributeConverter.kt")
    }
}
