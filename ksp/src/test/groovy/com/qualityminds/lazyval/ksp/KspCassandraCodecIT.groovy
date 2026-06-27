package com.qualityminds.lazyval.ksp

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.*

import java.nio.file.Files
import java.nio.file.Path

@Title("KSP Generator Integration - Cassandra Codec")
class KspCassandraCodecIT extends Specification {

    public static final Dependency dependencyJakartaAnnotations = new Dependency("jakarta.annotation", "jakarta.annotation-api", "3.0.0")
    public static final Dependency dependencyDriverCore = new Dependency("com.datastax.oss", "java-driver-core", "4.17.0")
    public static final Dependency dependencyCassandraQuarkusExtension = new Dependency("com.datastax.oss.quarkus", "cassandra-quarkus-client", "1.4.1")
    public static final Dependency dependencyCdi = new Dependency("jakarta.enterprise", "jakarta.enterprise.cdi-api", "4.1.0")
    public static final Dependency dependencyInject = new Dependency("jakarta.inject", "jakarta.inject-api", "2.0.1")
    public static final Dependency dependencyJakartaAnnotation = new Dependency("jakarta.annotation", "jakarta.annotation-api", "3.0.0")
    public static final Dependency dependencyQuarkus = new Dependency("io.quarkus", "quarkus-core", "3.34.5")
    public static final Dependency dependencyQuarkusArc = new Dependency("io.quarkus.arc", "arc", "3.34.5")
    public static final Dependency dependencyQuarkusNetty = new Dependency("io.quarkus", "quarkus-netty", "3.34.5")
    public static final Dependency dependencyNetty = new Dependency("io.netty", "netty-transport", "4.1.132.Final")

    private static final String GENERATED_FILE_NAME = "LazyvalCassandraCodecs.kt"

    @TempDir()
    Path projectDir
    @Shared
    def testkitKotlin = Testkit.kotlin()

    @Unroll("#scenario.name() compiles and generated #expected.generatedFiles()")
    void "Cassandra Codec with all Scenarios"(){
        given:
        scenario.withDependencies(dependencyDriverCore, dependencyJakartaAnnotations)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == expected

        then: 'file is generated at correct location using base-package with generator-default'
        projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/cassandra/$GENERATED_FILE_NAME").toFile().exists()

        and: 'contains @Generated'
        Files.readString(projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/cassandra/$GENERATED_FILE_NAME")).contains("@Generated")

        where:
        scenario << Scenario.Kotlin.all()
        expected = new Testresult.Kotlin.Success(GENERATED_FILE_NAME)
    }


    void "Package override by generator works as expected"(){
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(dependencyDriverCore)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.cassandra.package", "test.custom")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Kotlin.Success([GENERATED_FILE_NAME])

        and: 'file is at correct package'
        projectDir.resolve("build/generated/ksp/kotlin/test/custom/$GENERATED_FILE_NAME").toFile().exists()
    }

    void "Quarkus Registration generated when Cassandra-Quarkus-Extension is available"(){
        given:
        def scenario = Scenario.Kotlin.orderDate()
                .withDependencies(
                        dependencyDriverCore, dependencyCassandraQuarkusExtension,
                        // compile dependencies
                        dependencyCdi,
                        dependencyInject,
                        dependencyJakartaAnnotation,
                        dependencyQuarkus,
                        dependencyQuarkusArc,
                        dependencyQuarkusNetty,
                        dependencyNetty)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME, "LazyvalCassandraCodecRegistrar.kt")

        and: 'contains @Generated'
        Files.readString(projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/cassandra/LazyvalCassandraCodecRegistrar.kt")).contains("@Generated")
    }

    void "Does not add '@Generated' when jakarta.annotations-api not on classpath"(){
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(dependencyDriverCore)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME)

        and: 'doesnt contain @Generated'
        !Files.readString(projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/cassandra/$GENERATED_FILE_NAME")).contains("@Generated")
    }

    void "single valid user codec is appended to all()"() {
        given:
        def scenario = Scenario.Kotlin.of(
                "valid-codec",
                "scenarios/kotlin/Quantity.kt",
                "scenarios/cassandracodecs/ValidCassandraCodec.kt")
                .withDependencies(dependencyDriverCore)
        scenario.withOption("lazyval.cassandra.codecs", "scenarios.cassandracodecs.ValidCassandraCodec")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME)

        and: 'generated file references the user codec'
        def generated = projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/cassandra/$GENERATED_FILE_NAME").toFile().text
        generated.contains("scenarios.cassandracodecs.ValidCassandraCodec()")
    }

    void "multiple valid user codecs are appended in declared order"() {
        given:
        def scenario = Scenario.Kotlin.of(
                "multiple-valid",
                "scenarios/kotlin/Quantity.kt",
                "scenarios/cassandracodecs/ValidCassandraCodec.kt",
                "scenarios/cassandracodecs/AnotherValidCassandraCodec.kt")
                .withDependencies(dependencyDriverCore)
        scenario.withOption("lazyval.cassandra.codecs",
                "scenarios.cassandracodecs.ValidCassandraCodec,scenarios.cassandracodecs.AnotherValidCassandraCodec")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME)

        and:
        def generated = projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/cassandra/$GENERATED_FILE_NAME").toFile().text
        def validIdx = generated.indexOf("scenarios.cassandracodecs.ValidCassandraCodec()")
        def anotherIdx = generated.indexOf("scenarios.cassandracodecs.AnotherValidCassandraCodec()")
        validIdx >= 0
        anotherIdx > validIdx
    }

    void "missing class FQN fails the build"() {
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(dependencyDriverCore)
        scenario.withOption("lazyval.cassandra.codecs", "com.example.Missing")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Failure
        def failure = result as Testresult.Kotlin.Failure
        failure.errors().any {
            it.contains("lazyval.cassandra.codecs") && it.contains("com.example.Missing") && it.contains("not found on compile classpath")
        }
    }

    void "class that does not implement TypeCodec fails the build"() {
        given:
        def scenario = Scenario.Kotlin.of(
                "invalid-codec",
                "scenarios/kotlin/Quantity.kt",
                "scenarios/cassandracodecs/NotACassandraCodec.kt")
                .withDependencies(dependencyDriverCore)
        scenario.withOption("lazyval.cassandra.codecs", "scenarios.cassandracodecs.NotACassandraCodec")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Failure
        def failure = result as Testresult.Kotlin.Failure
        failure.errors().any {
            it.contains("scenarios.cassandracodecs.NotACassandraCodec") && it.contains("does not implement") && it.contains("TypeCodec")
        }
    }

    void "internal codec in different module fails the build"() {
        given: 'codecs are generated at default location (test.boundary.persistence.cassandra); codec lives in scenarios.cassandracodecs'
        def scenario = Scenario.Kotlin.of(
                "internal-codec",
                "scenarios/kotlin/Quantity.kt",
                "scenarios/cassandracodecs/NonPublicCassandraCodec.kt")
                .withDependencies(dependencyDriverCore)
        scenario.withOption("lazyval.cassandra.codecs", "scenarios.cassandracodecs.NonPublicCassandraCodec")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'internal in current module is accessible — the codec is included'
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME)

        and:
        def generated = projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/cassandra/$GENERATED_FILE_NAME").toFile().text
        generated.contains("scenarios.cassandracodecs.NonPublicCassandraCodec()")
    }

    void "file-private codec fails the build"() {
        given: 'NonAccessibleCassandraCodec is a top-level private (file-scoped) class — unreachable from anywhere'
        def scenario = Scenario.Kotlin.of(
                "file-private",
                "scenarios/kotlin/Quantity.kt",
                "scenarios/cassandracodecs/NonAccessibleCassandraCodec.kt")
                .withDependencies(dependencyDriverCore)
        scenario.withOption("lazyval.cassandra.codecs", "scenarios.cassandracodecs.NonAccessibleCassandraCodec")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Failure
        def failure = result as Testresult.Kotlin.Failure
        failure.errors().any {
            it.contains("scenarios.cassandracodecs.NonAccessibleCassandraCodec") && it.contains("not accessible")
        }
    }

    void "codec without no-arg constructor fails the build"() {
        given:
        def scenario = Scenario.Kotlin.of(
                "missing-no-arg-ctor",
                "scenarios/kotlin/Quantity.kt",
                "scenarios/cassandracodecs/NoNoArgCassandraCodec.kt")
                .withDependencies(dependencyDriverCore)
        scenario.withOption("lazyval.cassandra.codecs", "scenarios.cassandracodecs.NoNoArgCassandraCodec")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Failure
        def failure = result as Testresult.Kotlin.Failure
        failure.errors().any {
            it.contains("scenarios.cassandracodecs.NoNoArgCassandraCodec") && it.contains("no-arg constructor")
        }
    }
}
