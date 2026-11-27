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
 * AI evaluation finding K22 (GitHub issue "Evaluation: ksp", label evaluation). This specification intentionally
 * fails on main: it documents the expected behaviour, not the current one.
 * <p>
 * Member names reach generated code as plain strings. A payload property named {@code `object`} is written into
 * the JPA converter unescaped ({@code type?.object}), which kotlinc reads as syntax errors; a companion factory
 * named {@code new} is legal Kotlin but becomes {@code JavaKeywordFactory.Companion.new(value)} in the MapStruct
 * mapper, which javac rejects. Either outcome is accepted here: code that compiles, or a Lazyval error.
 */
@Title("Evaluation K22 - identifiers that are keywords")
class K22UnescapedIdentifiersIT extends Specification {

    static final Dependency JPA = new Dependency("jakarta.persistence", "jakarta.persistence-api", "3.2.0")
    static final Dependency MAPSTRUCT = new Dependency("org.mapstruct", "mapstruct", "1.6.3")

    @TempDir
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    @Unroll("K22 #identifier compiles in #generator output or is rejected by Lazyval")
    void "K22 a keyword used as identifier ends in compiling code or a Lazyval error, never a compiler error"() {
        given:
        def scenario = Scenario.Kotlin.ofSingle("scenarios/evaluation/k22/$source").withDependencies(dependency)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        !(result instanceof Testresult.Kotlin.Failure) ||
                (result as Testresult.Kotlin.Failure).errors().every { it.startsWith("Lazyval:") }

        where:
        identifier                       | generator   | source                    | dependency
        'a property named `object`'      | 'JPA'       | 'KeywordProperty.kt'      | JPA
        'a factory named new'            | 'MapStruct' | 'JavaKeywordFactory.kt'   | MAPSTRUCT
    }
}
