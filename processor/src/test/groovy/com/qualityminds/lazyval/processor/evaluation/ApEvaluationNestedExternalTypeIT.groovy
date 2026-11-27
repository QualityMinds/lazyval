package com.qualityminds.lazyval.processor.evaluation

import com.qualityminds.lazyval.processor.ApJpaIT
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
 * AI evaluation finding P21 (GitHub issue "Evaluation: processor", label evaluation): the check that a type listed
 * in {@code @LazyvalConfiguration.externalTypes} does not belong to the current compilation unit only compares
 * against the round's root elements ({@code LazyvalEnvironment.getConfiguredValues}), so a nested type of the
 * compilation unit passes it. An unannotated one is then generated as if it were external; an annotated one is
 * processed twice and javac aborts with a {@code FilerException}.
 * <p>
 * This specification intentionally fails on main; it documents the finding until it is fixed. Expected: the same
 * error a top-level type of the compilation unit gets (see {@code ApIT}).
 */
@Title("Evaluation P21 - nested types of the compilation unit listed as external")
class ApEvaluationNestedExternalTypeIT extends Specification {

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    @Unroll("P21 listing the nested #kind type '#nestedType' as external is rejected")
    void "P21 a nested type of the current compilation unit listed in externalTypes is rejected"() {
        given:
        def scenario = Scenario.Java.of("p21 $nestedType", "scenarios/evaluation/p21/Outer.java", configuration)
                .withDependencies(ApJpaIT.dependencyJakartaPersistence)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: "on main: $mainBehavior"
        result == new Testresult.Java.Failure("Lazyval: Type 'scenarios.evaluation.p21.$nestedType' listed in " +
                "@LazyvalConfiguration.externalTypes belongs to the current compilation unit. " +
                "Annotate it with @LazyValue directly.")

        where:
        kind          | nestedType    | configuration                                   | mainBehavior
        "unannotated" | "Outer.Plain" | "scenarios/evaluation/p21/PlainAsExternal.java" | "a converter is generated as for an external type"
        "@LazyValue"  | "Outer.Code"  | "scenarios/evaluation/p21/CodeAsExternal.java"  | "processed twice, javac throws a FilerException"
    }
}
