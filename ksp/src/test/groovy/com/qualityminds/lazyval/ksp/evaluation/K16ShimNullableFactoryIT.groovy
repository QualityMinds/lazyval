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
 * AI evaluation finding K16 (GitHub issue "Evaluation: ksp", label evaluation). This specification intentionally
 * fails on main: it documents the expected behaviour, not the current one.
 * <p>
 * The Java-access shim of a value-class payload declares {@code fun of(payload): Qty} and calls the
 * domain-primitive's factory, which may return {@code Qty?}. On main kotlinc rejects the shim ("Return type
 * mismatch: expected 'Qty', actual 'Qty?'"), and because the shim is emitted for every such domain-primitive, a
 * module without any generator dependency fails to compile as well.
 */
@Title("Evaluation K16 - Java-access shim and a nullable-returning factory")
class K16ShimNullableFactoryIT extends Specification {

    static final Dependency MAPSTRUCT = new Dependency("org.mapstruct", "mapstruct", "1.6.3")

    @TempDir
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    void "K16 without any generator on the classpath nothing is generated and the module compiles"() {
        given:
        def scenario = Scenario.Kotlin.ofSingle("scenarios/evaluation/k16/NullableFactoryValueClassPayload.kt")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.NothingGenerated()
    }

    void "K16 a generator reaching the value-class payload through the shim produces code that compiles"() {
        given:
        def scenario = Scenario.Kotlin.ofSingle("scenarios/evaluation/k16/NullableFactoryValueClassPayload.kt")
                .withDependencies(MAPSTRUCT)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Success
        (result as Testresult.Kotlin.Success).generatedFiles().contains("LazyvalMapper.java")
    }
}
