package com.qualityminds.lazyval.ksp.evaluation

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * AI evaluation finding T7, Kotlin side: {@code Testresult.Kotlin} is a sealed result with a
 * {@code Failure} variant, yet a generator crashing inside KSP surfaces from {@code Testkit.kotlin().run}
 * as {@code RuntimeException: Toolchain execution failed}. A test written against the sealed result never
 * sees the {@code Failure} it was designed to report.
 * <p>
 * This test intentionally fails on {@code main}. The crashing generator is the test-only
 * {@link ThrowingProbeGenerator}, active only for scenarios containing its marker class.
 */
class KotlinGeneratorExceptionContractIT extends Specification {

    @TempDir
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    void "T7 a generator throwing an exception yields a Failure result"() {
        given:
        def scenario = Scenario.Kotlin.of('isbn with throwing generator',
                'scenarios/kotlin/Isbn.kt',
                'scenarios/evaluation/ThrowingProbe.kt')

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'the crash is reported through the sealed result, not as an exception'
        noExceptionThrown()
        result instanceof Testresult.Kotlin.Failure
        and: 'the failure names the cause'
        (result as Testresult.Kotlin.Failure).errors().anySatisfy { it.contains(ThrowingProbeGenerator.MESSAGE) }
    }
}
