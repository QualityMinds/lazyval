package com.qualityminds.lazyval.processor.evaluation

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import com.qualityminds.lazyval.testkit.scenarios.ScenarioFactory
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * AI evaluation finding T7: {@code Testresult.Java} is a sealed result with a {@code Failure} variant, yet
 * the Java toolchain turns every exception into a {@code RuntimeException}: a generator crashing inside
 * javac surfaces as {@code RuntimeException: Failed to collect generated files}, and an option key javac
 * refuses as {@code RuntimeException: Failed to setup compiler task}. A test written against the sealed
 * result never sees the {@code Failure} it was designed to report.
 * <p>
 * These tests intentionally fail on {@code main}; each feature documents one symptom of the finding. The
 * crashing generator is the test-only {@link ThrowingProbeGenerator}, active only for scenarios containing
 * its marker class.
 */
class GeneratorExceptionContractIT extends Specification {

    @TempDir
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    void "T7 a generator throwing an exception yields a Failure result"() {
        given:
        def scenario = Scenario.Java.of('isbn with throwing generator',
                'scenarios/java/Isbn.java',
                'scenarios/evaluation/ThrowingProbe.java')

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'the crash is reported through the sealed result, not as an exception'
        noExceptionThrown()
        result instanceof Testresult.Java.Failure
        and: 'the failure names the cause'
        (result as Testresult.Java.Failure).errors().anySatisfy { it.contains(ThrowingProbeGenerator.MESSAGE) }
    }

    void "T7 an option key javac does not accept is rejected by withOption or reported as a Failure"() {
        when: 'a generator option is spelled with a hyphen, like the stock generator id jackson-2'
        def outcome = rejectedOrRun {
            Scenario.Java.isbn().withOption('lazyval.my-generator.package', 'com.acme')
        }

        then: 'the mistake surfaces at the call naming the option, or through the sealed result'
        outcome instanceof IllegalArgumentException || outcome instanceof Testresult.Java.Failure
    }

    /**
     * Returns the {@code IllegalArgumentException} if configuring the factory rejects the option, otherwise
     * whatever running it produces: the result, or the exception it throws.
     */
    private Object rejectedOrRun(Closure<ScenarioFactory<Scenario.Java>> configure) {
        ScenarioFactory<Scenario.Java> factory
        try {
            factory = configure.call()
        } catch (IllegalArgumentException rejected) {
            return rejected
        }
        try {
            return testkitJava.run(projectDir, factory)
        } catch (RuntimeException e) {
            return e
        }
    }
}
