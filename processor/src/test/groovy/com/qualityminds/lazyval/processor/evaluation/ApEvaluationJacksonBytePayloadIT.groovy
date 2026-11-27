package com.qualityminds.lazyval.processor.evaluation

import com.qualityminds.lazyval.processor.ApJacksonIT
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
 * AI evaluation finding P14 (GitHub issue "Evaluation: processor", label evaluation), byte part: the generated
 * Jackson serializer writes a {@code byte} payload with {@code gen.writeString(String.valueOf(..))}
 * ({@code JacksonCodegen.generateSerializer}), so {@code Level((byte) 5)} becomes {@code "5"} although Jackson
 * writes a {@code byte} as a number.
 * <p>
 * This specification intentionally fails on main; it documents the finding until it is fixed. Expected: a
 * {@code byte} payload is written as a JSON number. Checked at runtime: the scenario's compiled classes, generated
 * module included, are loaded next to the Jackson jars and serialized with a plain mapper.
 */
@Title("Evaluation P14 - Jackson byte payload")
class ApEvaluationJacksonBytePayloadIT extends Specification {

    /** Not needed to compile against databind, but at runtime; the version Jackson 2.21 and 3.1 both use. */
    private static final Dependency JACKSON_ANNOTATIONS = new Dependency("com.fasterxml.jackson.core", "jackson-annotations", "2.21")

    /** Jackson 2: a plain ObjectMapper with the module registered. */
    private static final Closure JACKSON_2_MAPPER = { ClassLoader loader, Object module ->
        loader.loadClass("com.fasterxml.jackson.databind.ObjectMapper").getConstructor().newInstance().registerModule(module)
    }

    /** Jackson 3: mappers are immutable, so the module goes in through the builder. */
    private static final Closure JACKSON_3_MAPPER = { ClassLoader loader, Object module ->
        loader.loadClass("tools.jackson.databind.json.JsonMapper").getMethod("builder").invoke(null).addModule(module).build()
    }

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    @Unroll("P14 a byte payload is written as a JSON number (#generatedFile)")
    void "P14 a byte payload is written as a JSON number, the way Jackson writes a byte"() {
        given: 'record Level(byte value), among the testkit primitive payloads'
        def scenario = Scenario.Java.primitivePayloads().withDependencies(core, databind)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(generatedFile)

        when: 'Level((byte) 5) is serialized by a mapper with the generated module'
        def json = withCompiledClasses([core, databind, JACKSON_ANNOTATIONS]) { ClassLoader loader ->
            def module = loader.loadClass("test.${generatedFile - '.java'}").getConstructor().newInstance()
            def level = loader.loadClass('scenarios.java.PrimitivePayloads$Level').getConstructor(byte).newInstance(5 as byte)
            mapperWithModule(loader, module).writeValueAsString(level)
        }

        then: 'on main, the generated serializer writes the string "5"'
        json == "5"

        where:
        core                                 | databind                                 | generatedFile                | mapperWithModule
        ApJacksonIT.dependencyJackson_2_Core | ApJacksonIT.dependencyJackson_2_Databind | "LazyvalJackson2Module.java" | JACKSON_2_MAPPER
        ApJacksonIT.dependencyJackson_3_Core | ApJacksonIT.dependencyJackson_3_Databind | "LazyvalJacksonModule.java"  | JACKSON_3_MAPPER
    }

    /** Runs {@code action} against the scenario's compiled classes and {@code dependencies}, isolated from the test's classpath. */
    private <T> T withCompiledClasses(List<Dependency> dependencies, Closure<T> action) {
        def urls = [testkitJava.generatedResourcePath(projectDir, "").toUri().toURL()]
        dependencies.each { dependency -> dependency.resolve().toSet().each { urls << it.toURI().toURL() } }
        new URLClassLoader(urls as URL[], ClassLoader.platformClassLoader).withCloseable { action(it) }
    }
}
