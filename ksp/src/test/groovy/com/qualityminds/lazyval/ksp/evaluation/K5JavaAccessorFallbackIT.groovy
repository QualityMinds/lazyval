package com.qualityminds.lazyval.ksp.evaluation

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title

import java.nio.file.Files
import java.nio.file.Path

/**
 * AI evaluation finding K5 (GitHub issue "Evaluation: ksp", label evaluation). This specification intentionally
 * fails on main: it documents the expected behaviour, not the current one.
 * <p>
 * For a Java type, {@code AccessorLookup.findAccessor} falls back to the first public zero-argument method
 * returning the payload type when neither the property name nor its JavaBean spelling matches. {@code Celsius}
 * declares the derived view {@code asFahrenheit()} before its accessor {@code celsius()}, so on main the generated
 * JPA converter persists Fahrenheit and reads it back through {@code Celsius.of(double)} as Celsius.
 */
@Title("Evaluation K5 - type-only accessor fallback for Java types")
class K5JavaAccessorFallbackIT extends Specification {

    static final Dependency JPA = new Dependency("jakarta.persistence", "jakarta.persistence-api", "3.2.0")

    @TempDir
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    void "K5 a Java external type is read through its accessor, not the first method of the payload type"() {
        given: 'Celsius from a Java library, listed as external type, with a JPA converter to generate'
        def scenario = Scenario.Kotlin.ofSingle("scenarios/evaluation/k5/CelsiusConfig.kt")
                .withJavaLibrary("scenarios/evaluation/k5/Celsius.java")
                .withDependencies(JPA)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success("CelsiusAttributeConverter.kt")

        and: 'the column holds what Celsius.of takes back'
        def converter = Files.readString(testkitKotlin.generatedKotlinSourcePath(projectDir,
                "test/boundary/persistence/jpa/CelsiusAttributeConverter.kt"))
        !converter.contains("asFahrenheit()")
        converter.contains(".celsius()")
    }
}
