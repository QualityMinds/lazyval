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
 * AI evaluation finding K12 (GitHub issue "Evaluation: ksp", label evaluation). This specification intentionally
 * fails on main: it documents the expected behaviour, not the current one.
 * <p>
 * KSP reports a type alias rather than the type it stands for. Only the array rule looks through it
 * ({@code failing/ArrayPayloadTypeAlias.kt}); the nullable-payload rule and the value-class unwrapping do not, and
 * the generators write the alias into Java, where it does not exist. On main even a plain {@code String} behind an
 * alias fails in javac, a nullable payload behind an alias passes validation and fails in javac, and a value class
 * behind an alias is not unwrapped: the MapStruct mapper calls the mangled getter and the alias "class", the JPA
 * converter stores the value class itself.
 */
@Title("Evaluation K12 - type aliases are not expanded")
class K12TypeAliasPayloadIT extends Specification {

    static final Dependency JPA = new Dependency("jakarta.persistence", "jakarta.persistence-api", "3.2.0")
    static final Dependency MAPSTRUCT = new Dependency("org.mapstruct", "mapstruct", "1.6.3")

    @TempDir
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    void "K12 a nullable payload behind a type alias is rejected like any nullable payload"() {
        given: 'typealias MaybeString = String?'
        def scenario = Scenario.Kotlin.ofSingle("scenarios/evaluation/k12/NullableAlias.kt")
                .withDependencies(MAPSTRUCT, JPA)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'the error failing/NullablePayload.kt gets'
        result == new Testresult.Kotlin.Failure("Lazyval: Payload must not be nullable. Please use a non-nullable type.")
    }

    void "K12 a String payload behind a type alias is written into Java as String"() {
        given: 'typealias Code = String'
        def scenario = Scenario.Kotlin.ofSingle("scenarios/evaluation/k12/StringAlias.kt")
                .withDependencies(MAPSTRUCT, JPA)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'the Java mapper compiles'
        result == new Testresult.Kotlin.Success("LazyvalMapper.java", "CodedAttributeConverter.kt")
    }

    @Unroll("K12 #alias is unwrapped like the value class it stands for")
    void "K12 a value-class payload behind a type alias is unwrapped like the value class"() {
        given:
        def scenario = Scenario.Kotlin.ofSingle("scenarios/evaluation/k12/${source}").withDependencies(MAPSTRUCT, JPA)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'the files the value class without the alias gets, so the Java mapper compiles'
        result == new Testresult.Kotlin.Success(
                "LazyvalMapper.java", "${type}AttributeConverter.kt".toString(), "${type}JvmAccess.kt".toString())

        and: 'the column holds the unwrapped type'
        Files.readString(testkitKotlin.generatedKotlinSourcePath(projectDir,
                "test/boundary/persistence/jpa/${type}AttributeConverter.kt"))
                .contains("AttributeConverter<${type}?, ${underlying}?>")

        where:
        alias                           | source               | type    | underlying
        'typealias Money = Cents'       | 'ValueClassAlias.kt' | 'Price' | 'Long'
        'typealias Count = UInt'        | 'UnsignedAlias.kt'   | 'Stock' | 'Int'
    }
}
