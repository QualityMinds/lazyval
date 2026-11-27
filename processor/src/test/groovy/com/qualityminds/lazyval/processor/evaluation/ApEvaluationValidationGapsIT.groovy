package com.qualityminds.lazyval.processor.evaluation

import com.qualityminds.lazyval.processor.ApJacksonIT
import com.qualityminds.lazyval.processor.ApJpaIT
import com.qualityminds.lazyval.processor.ApMapstructIT
import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title
import spock.lang.Unroll

import java.nio.file.Path

/**
 * AI evaluation finding P11 (GitHub issue "Evaluation: processor", label evaluation): {@code LazyvalElementValidator}
 * accepts shapes that generated code cannot handle, so the build fails on the generated sources instead — with
 * javac errors that point at code the user did not write.
 * <p>
 * This specification intentionally fails on main; it documents the finding until it is fixed. Expected: each
 * shape is rejected by Lazyval itself, every error being a Lazyval diagnostic.
 */
@Title("Evaluation P11 - shapes the validator lets through")
class ApEvaluationValidationGapsIT extends Specification {

    private static final List<Dependency> JACKSON_2 = [ApJacksonIT.dependencyJackson_2_Core, ApJacksonIT.dependencyJackson_2_Databind]

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    @Unroll("P11 #shape is rejected with a Lazyval error instead of generating uncompilable code")
    void "P11 shapes generated code cannot handle are rejected by validation"() {
        given:
        def scenario = Scenario.Java.ofSingle("scenarios/evaluation/p11/$source").withDependencies(dependencies)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'on main, javac reports errors in the generated sources instead'
        result instanceof Testresult.Java.Failure
        (result as Testresult.Java.Failure).errors().every { it.startsWith("Lazyval:") }

        where:
        shape                                                  | source               | dependencies
        "a non-static inner class (JPA)"                       | "inner/Outer.java"   | [ApJpaIT.dependencyJakartaPersistence]
        "a public type nested in a package-private one (JPA)"  | "hidden/Holder.java" | [ApJpaIT.dependencyJakartaPersistence]
        "a factory declaring a checked exception (JPA)"        | "checked/Isbn.java"  | [ApJpaIT.dependencyJakartaPersistence]
        "a factory declaring a checked exception (MapStruct)"  | "checked/Isbn.java"  | [ApMapstructIT.dependencyMapstruct]
        "a generic domain-primitive (JPA)"                     | "generic/Id.java"    | [ApJpaIT.dependencyJakartaPersistence]
        "a parameterized payload type (Jackson)"               | "collection/Tags.java" | JACKSON_2
    }
}
