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
 * AI evaluation finding K1 (GitHub issue "Evaluation: ksp", label evaluation). This specification intentionally
 * fails on main: it documents the expected behaviour, not the current one.
 * <p>
 * Generators name their output after the domain-primitive's flattened simple name, so two domain-primitives
 * whose names flatten alike claim the same file: {@code a.Email} and {@code b.Email}, or a nested
 * {@code Outer.Inner} and a top-level {@code OuterInner}. KSP throws {@code FileAlreadyExistsException} for the
 * second {@code createNewFile} of one run, and {@code LazyvalSymbolProcessor.writeKotlinFile},
 * {@code writeJavaFile} and {@code writeServiceLoaderFiles} swallow it as an incremental-build leftover. On main
 * the build succeeds with one converter (or one value extractor, while the services file lists one provider and
 * the log claims two) and no diagnostic.
 */
@Title("Evaluation K1 - colliding generated names")
class K1CollidingOutputsIT extends Specification {

    static final Dependency JPA = new Dependency("jakarta.persistence", "jakarta.persistence-api", "3.2.0")
    static final Dependency BEAN_VALIDATION = new Dependency("jakarta.validation", "jakarta.validation-api", "3.1.0")

    @TempDir
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    @Unroll("K1 #collision with the #generator generator is reported instead of silently dropping one output")
    void "K1 two domain-primitives with the same simple name are reported instead of silently dropped"() {
        given:
        def scenario = Scenario.Kotlin.of(collision, sources as String[]).withDependencies(dependency)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'a Lazyval error that names both domain-primitives, rather than a build missing one of them'
        result instanceof Testresult.Kotlin.Failure
        def errors = (result as Testresult.Kotlin.Failure).errors()
        errors.every { it.startsWith("Lazyval:") }
        errors.any { it.contains(first) }
        errors.any { it.contains(second) }

        where:
        collision                    | generator        | dependency      | first                              | second
        'a.Email and b.Email'        | 'jpa'            | JPA             | 'evaluation.k1.a.Email'            | 'evaluation.k1.b.Email'
        'a.Email and b.Email'        | 'beanvalidation' | BEAN_VALIDATION | 'evaluation.k1.a.Email'            | 'evaluation.k1.b.Email'
        'Outer.Inner and OuterInner' | 'jpa'            | JPA             | 'evaluation.k1.nested.Outer.Inner' | 'evaluation.k1.nested.OuterInner'

        sources = collision.startsWith('a.')
                ? ['scenarios/evaluation/k1/a/Email.kt', 'scenarios/evaluation/k1/b/Email.kt']
                : ['scenarios/evaluation/k1/nested/Outer.kt', 'scenarios/evaluation/k1/nested/OuterInner.kt']
    }
}
