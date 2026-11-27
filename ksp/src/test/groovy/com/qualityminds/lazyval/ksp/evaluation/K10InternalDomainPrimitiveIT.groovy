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
 * AI evaluation finding K10 (GitHub issue "Evaluation: ksp", label evaluation). This specification intentionally
 * fails on main: it documents the expected behaviour, not the current one.
 * <p>
 * {@code rules.adoc} ({@code [#visibility]}) and {@code edge/InternalDomainPrimitive.kt} accept an
 * {@code internal} domain-primitive, but that scenario only runs the MapStruct generator, whose output is Java.
 * Every generator emitting Kotlin declares public members over the type, and kotlinc rejects them on main:
 * "'public' function exposes its 'internal' parameter type" (JPA, Bean Validation, JSON-B), "'public' property
 * exposes its 'internal' type argument" (Lettuce).
 */
@Title("Evaluation K10 - internal domain-primitive with Kotlin output")
class K10InternalDomainPrimitiveIT extends Specification {

    static final Dependency JPA = new Dependency("jakarta.persistence", "jakarta.persistence-api", "3.2.0")
    static final Dependency BEAN_VALIDATION = new Dependency("jakarta.validation", "jakarta.validation-api", "3.1.0")
    static final Dependency JSONB_API = new Dependency("jakarta.json.bind", "jakarta.json.bind-api", "3.0.1")
    static final Dependency LETTUCE = new Dependency("io.lettuce", "lettuce-core", "7.5.2.RELEASE")

    @TempDir
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    @Unroll("K10 an internal domain-primitive compiles with the #generator generator")
    void "K10 an internal domain-primitive, documented as accepted, produces Kotlin that compiles"() {
        given:
        def scenario = Scenario.Kotlin.ofSingle("scenarios/evaluation/k10/Secret.kt").withDependencies(dependency)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(generatedFiles)

        where:
        generator        | dependency      | generatedFiles
        'jpa'            | JPA             | ['SecretAttributeConverter.kt']
        'beanvalidation' | BEAN_VALIDATION | ['SecretValueExtractor.kt', 'SecretValueExtractorBase.java']
        'jsonb'          | JSONB_API       | ['LazyvalJsonbAdapters.kt']
        'lettuce'        | LETTUCE         | ['LazyvalLettuceCodecs.kt']
    }
}
