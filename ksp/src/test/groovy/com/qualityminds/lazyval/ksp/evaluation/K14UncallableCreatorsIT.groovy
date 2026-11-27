package com.qualityminds.lazyval.ksp.evaluation

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
 * AI evaluation finding K14 (GitHub issue "Evaluation: ksp", label evaluation). This specification intentionally
 * fails on main: it documents the expected behaviour, not the current one.
 * <p>
 * The validator judges the declarations it matched by their own visibility and signature, so it accepts shapes
 * generated code cannot call, and the build then fails in kotlinc or javac, on generated code the user did not
 * write, instead of with a Lazyval error on the declaration to change. Each row compiles with MapStruct (Java
 * output) and JPA (Kotlin output), like {@code KspIT}'s "Failing Requirement".
 */
@Title("Evaluation K14 - accepted shapes that generated code cannot call")
class K14UncallableCreatorsIT extends Specification {

    static final Dependency JPA = new Dependency("jakarta.persistence", "jakarta.persistence-api", "3.2.0")
    static final Dependency MAPSTRUCT = new Dependency("org.mapstruct", "mapstruct", "1.6.3")

    @TempDir
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    @Unroll("K14 #shape is rejected with a Lazyval error")
    void "K14 a shape generated code cannot call is rejected by Lazyval, not by the compiler"() {
        given:
        def scenario = Scenario.Kotlin.ofSingle("scenarios/evaluation/k14/$source").withDependencies(MAPSTRUCT, JPA)
        if (library) {
            scenario.withJavaLibrary("scenarios/evaluation/k14/$library")
        }

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'only Lazyval errors, no compiler errors on generated code'
        result instanceof Testresult.Kotlin.Failure
        (result as Testresult.Kotlin.Failure).errors().every { it.startsWith("Lazyval:") }

        where:
        shape                                                       | source                       | library
        'a public factory in a private companion object'            | 'PrivateCompanionFactory.kt' | null
        'a public nested class in a private class'                  | 'NestedInPrivateClass.kt'    | null
        'an inner class'                                            | 'InnerClass.kt'              | null
        'a suspend factory'                                         | 'SuspendFactory.kt'          | null
        'a factory deprecated with level HIDDEN'                    | 'HiddenFactory.kt'           | null
        'a factory requiring opt-in at level ERROR'                 | 'OptInFactory.kt'            | null
        'an external Java type with a public field and no accessor' | 'CodeConfig.kt'              | 'Code.java'
    }
}
