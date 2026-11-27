package com.qualityminds.lazyval.processor.evaluation

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * AI evaluation finding T6: generator instances live as long as the test JVM. {@code LazyvalProcessor}
 * loads them once, in a static initializer, and the Testkit loads the processor through a class loader
 * whose parent is the test classpath, so every run in the JVM talks to the same generator objects. Any
 * state a generator keeps changes the outcome of later, unrelated runs and makes results depend on
 * test order.
 * <p>
 * This test intentionally fails on {@code main}. It uses the test-only {@link StatefulProbeGenerator},
 * which is active only for scenarios containing its marker class.
 */
class GeneratorStateIT extends Specification {

    @TempDir
    Path firstProjectDir

    @TempDir
    Path secondProjectDir

    @Shared
    def testkitJava = Testkit.java()

    private static Scenario.Java statefulScenario() {
        Scenario.Java.of('isbn with stateful generator',
                'scenarios/java/Isbn.java',
                'scenarios/evaluation/StatefulProbe.java').build()
    }

    void "T6 running the same scenario twice yields the same result"() {
        when: 'the same scenario runs twice, each time in a fresh project dir'
        def first = testkitJava.run(firstProjectDir, statefulScenario())
        def second = testkitJava.run(secondProjectDir, statefulScenario())

        then: 'the second run is not influenced by the first'
        second == first
    }
}
