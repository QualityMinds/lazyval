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

import java.nio.file.Files
import java.nio.file.Path

/**
 * AI evaluation finding K18 (GitHub issue "Evaluation: ksp", label evaluation). This specification intentionally
 * fails on main: it documents the expected behaviour, not the current one.
 * <p>
 * {@code JacksonCodegen.generateSerializer} writes {@code Char} and {@code Byte} payloads alike, with
 * {@code gen.writeString(...)}. A {@code char} is a string in JSON, but Jackson writes a {@code byte} as a number,
 * and the generated deserializer reads it as one ({@code p.byteValue}); on main {@code Level(7)} becomes
 * {@code "7"}. Asserted on the generated source, because no integration-test module has a {@code Byte} payload.
 */
@Title("Evaluation K18 - byte payload written as a JSON string")
class K18JacksonBytePayloadIT extends Specification {

    static final Dependency JACKSON_2_CORE = new Dependency("com.fasterxml.jackson.core", "jackson-core", "2.21.2")
    static final Dependency JACKSON_2_DATABIND = new Dependency("com.fasterxml.jackson.core", "jackson-databind", "2.21.2")
    static final Dependency JACKSON_3_CORE = new Dependency("tools.jackson.core", "jackson-core", "3.1.0")
    static final Dependency JACKSON_3_DATABIND = new Dependency("tools.jackson.core", "jackson-databind", "3.1.0")

    @TempDir
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    @Unroll("K18 #generatedFile writes a Byte payload as a JSON number")
    void "K18 a Byte payload is written as a JSON number, as Jackson writes a byte"() {
        given: 'one domain-primitive per primitive kind, Level wrapping a Byte'
        def scenario = Scenario.Kotlin.primitivePayloads().withDependencies(databind, core)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(generatedFile)

        and: 'the serializer for Level writes a number'
        def module = Files.readString(testkitKotlin.generatedKotlinSourcePath(projectDir, "test/$generatedFile"))
        def start = module.indexOf("class PrimitivePayloadsLevelSerializer")
        def levelSerializer = module.substring(start, module.indexOf("private class", start + 1))
        !levelSerializer.contains("writeString")
        levelSerializer.contains("gen.writeNumber(")

        where:
        databind           | core           | generatedFile
        JACKSON_2_DATABIND | JACKSON_2_CORE | 'LazyvalJackson2Module.kt'
        JACKSON_3_DATABIND | JACKSON_3_CORE | 'LazyvalJacksonModule.kt'
    }
}
