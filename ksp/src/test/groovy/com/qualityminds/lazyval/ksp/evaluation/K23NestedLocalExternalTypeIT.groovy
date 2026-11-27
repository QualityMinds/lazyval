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
 * AI evaluation finding K23 (GitHub issue "Evaluation: ksp", label evaluation). This specification intentionally
 * fails on main: it documents the expected behaviour, not the current one.
 * <p>
 * {@code LazyvalKspEnvironment.configuredValues} decides whether a type listed in {@code externalTypes} belongs to
 * the current compilation unit by collecting the top-level declarations of every file. A nested type is not among
 * them, so on main {@code Holder.Nested} is processed as if it came from a library, where a top-level type gets the
 * error {@code KspIT} pins for {@code failing/LocalTypeAsExternal.kt}.
 */
@Title("Evaluation K23 - nested local type listed in externalTypes")
class K23NestedLocalExternalTypeIT extends Specification {

    static final Dependency JPA = new Dependency("jakarta.persistence", "jakarta.persistence-api", "3.2.0")

    @TempDir
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    void "K23 a nested type of the current module listed in externalTypes is reported like a top-level one"() {
        given:
        def scenario = Scenario.Kotlin.ofSingle("scenarios/evaluation/k23/NestedLocalTypeAsExternal.kt")
                .withDependencies(JPA)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Failure("Lazyval: Type 'evaluation.k23.Holder.Nested' listed in " +
                "@LazyvalConfiguration.externalTypes belongs to the current compilation unit. " +
                "Annotate it with @LazyValue directly.")
    }
}
