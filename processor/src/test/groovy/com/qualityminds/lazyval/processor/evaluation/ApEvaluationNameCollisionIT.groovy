package com.qualityminds.lazyval.processor.evaluation

import com.qualityminds.lazyval.processor.ApJpaIT
import com.qualityminds.lazyval.processor.ApMapstructIT
import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title
import spock.lang.Unroll

import java.nio.file.Path

/**
 * AI evaluation finding P4 (GitHub issue "Evaluation: processor", label evaluation): generated names are built
 * from a domain-primitive's flat name alone, so two types sharing it collide. A per-type generator recreates the
 * same file and javac aborts with an uncaught {@code FilerException}; an aggregate generator emits code that does
 * not compile.
 * <p>
 * This specification intentionally fails on main; it documents the finding until it is fixed. Expected: the
 * collision is reported as a Lazyval error naming both types.
 */
@Title("Evaluation P4 - generated-name collisions")
class ApEvaluationNameCollisionIT extends Specification {

    /** {@code scenarios.evaluation.p4.a.Email} and {@code scenarios.evaluation.p4.b.Email}. */
    private static final List<String> SAME_SIMPLE_NAME = [
            "scenarios/evaluation/p4/a/Email.java",
            "scenarios/evaluation/p4/b/Email.java"]

    /** {@code scenarios.evaluation.p4.flat.Order.Id} and {@code scenarios.evaluation.p4.flat.OrderId}. */
    private static final List<String> SAME_FLAT_NAME = [
            "scenarios/evaluation/p4/flat/Order.java",
            "scenarios/evaluation/p4/flat/OrderId.java"]

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    @Unroll("P4 #collision is reported as a Lazyval error naming both types")
    void "P4 domain-primitives whose generated names collide are reported instead of crashing"() {
        given:
        def scenario = Scenario.Java.of("p4 $collision", *sources).withDependencies(dependency)

        when: 'on main, a per-type generator makes javac throw instead of returning a result'
        def result = testkitJava.run(projectDir, scenario)

        then: 'the collision is reported by Lazyval, not by javac compiling generated code'
        result instanceof Testresult.Java.Failure
        def errors = (result as Testresult.Java.Failure).errors()
        errors.every { it.startsWith("Lazyval:") }
        errors.any { it.contains(first) && it.contains(second) }

        where:
        collision                                                        | sources          | dependency                           | first                                   | second
        "the same simple name in two packages (JPA, per type)"           | SAME_SIMPLE_NAME | ApJpaIT.dependencyJakartaPersistence | "scenarios.evaluation.p4.a.Email"       | "scenarios.evaluation.p4.b.Email"
        "a nested and a top-level type of one flat name (JPA, per type)" | SAME_FLAT_NAME   | ApJpaIT.dependencyJakartaPersistence | "scenarios.evaluation.p4.flat.Order.Id" | "scenarios.evaluation.p4.flat.OrderId"
        "the same simple name in two packages (MapStruct, aggregate)"    | SAME_SIMPLE_NAME | ApMapstructIT.dependencyMapstruct    | "scenarios.evaluation.p4.a.Email"       | "scenarios.evaluation.p4.b.Email"
    }
}
