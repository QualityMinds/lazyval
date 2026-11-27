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
 * AI evaluation finding K15 (GitHub issue "Evaluation: ksp", label evaluation). This specification intentionally
 * fails on main: it documents the expected behaviour, not the current one.
 * <p>
 * Type parameters and parameterized or mapped payload types pass validation and break code generation. On main a
 * generic value-class payload crashes KSP in {@code JavaAccessShimCodegen} and a generic domain-primitive in
 * {@code JpaGenerator} ({@code NoSuchElementException: No TypeParameter found for index T}, which the Testkit
 * rethrows); a {@code List<String>} payload makes the Jackson module use {@code List<String>::class.java}; and the
 * MapStruct mapper names {@code kotlin.collections.List} and {@code kotlin.Any}, which do not exist in Java.
 * <p>
 * Either outcome is accepted here: generated code that compiles, or Lazyval errors explaining why nothing can be
 * generated.
 */
@Title("Evaluation K15 - generic and mapped payload types")
class K15GenericAndMappedPayloadIT extends Specification {

    static final Dependency JPA = new Dependency("jakarta.persistence", "jakarta.persistence-api", "3.2.0")
    static final Dependency MAPSTRUCT = new Dependency("org.mapstruct", "mapstruct", "1.6.3")
    static final Dependency JACKSON_3_CORE = new Dependency("tools.jackson.core", "jackson-core", "3.1.0")
    static final Dependency JACKSON_3_DATABIND = new Dependency("tools.jackson.core", "jackson-databind", "3.1.0")

    @TempDir
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    @Unroll("K15 #shape compiles or is rejected by Lazyval")
    void "K15 a generic or mapped payload type ends in compiling code or a Lazyval error, never a crash or a compiler error"() {
        given:
        def scenario = Scenario.Kotlin.ofSingle("scenarios/evaluation/k15/$source").withDependencies(dependencies)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'the processor does not crash'
        noExceptionThrown()

        and: 'any error is a Lazyval diagnostic, not a compiler error on generated code'
        !(result instanceof Testresult.Kotlin.Failure) ||
                (result as Testresult.Kotlin.Failure).errors().every { it.startsWith("Lazyval:") }

        where:
        shape                                                 | source                          | dependencies
        'a generic value class Box<String> as payload'        | 'GenericValueClassPayload.kt'   | [MAPSTRUCT, JPA]
        'a generic domain-primitive Gen<T>'                   | 'GenericDomainPrimitive.kt'     | [JPA]
        'a List<String> payload with Jackson'                 | 'ListPayload.kt'                | [JACKSON_3_DATABIND, JACKSON_3_CORE]
        'a List<String> payload with MapStruct'               | 'ListPayload.kt'                | [MAPSTRUCT]
        'kotlin.Any and CharSequence payloads with MapStruct' | 'MappedTypePayload.kt'          | [MAPSTRUCT]
    }
}
