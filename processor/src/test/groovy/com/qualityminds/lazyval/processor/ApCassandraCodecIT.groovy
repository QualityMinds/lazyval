package com.qualityminds.lazyval.processor

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.*

import java.nio.file.Files
import java.nio.file.Path

@Title("Generator Integration - Cassandra Codec")
class ApCassandraCodecIT extends Specification {

    public static final Dependency dependencyDriverCore = new Dependency("com.datastax.oss", "java-driver-core", "4.17.0")
    public static final Dependency dependencyCassandraQuarkusExtension = new Dependency("com.datastax.oss.quarkus", "cassandra-quarkus-client", "1.4.1")
    public static final Dependency dependencyCdi = new Dependency("jakarta.enterprise", "jakarta.enterprise.cdi-api", "4.1.0")
    public static final Dependency dependencyInject = new Dependency("jakarta.inject", "jakarta.inject-api", "2.0.1")
    public static final Dependency dependencyJakartaAnnotation = new Dependency("jakarta.annotation", "jakarta.annotation-api", "3.0.0")
    public static final Dependency dependencyQuarkus = new Dependency("io.quarkus", "quarkus-core", "3.34.5")
    public static final Dependency dependencyQuarkusArc = new Dependency("io.quarkus.arc", "arc", "3.34.5")
    public static final Dependency dependencyQuarkusNetty = new Dependency("io.quarkus", "quarkus-netty", "3.34.5")
    public static final Dependency dependencyNetty = new Dependency("io.netty", "netty-transport", "4.1.132.Final")

    private static final String GENERATED_FILE_NAME = "LazyvalCassandraCodecs.java"

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    @Unroll("#scenario.name() compiles and generated #expected.generatedFiles()")
    void "Cassandra Codec with all Scenarios"(){
        given:
        scenario.withDependencies(dependencyDriverCore)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == expected

        and: 'contains @Generated'
        Files.readString(projectDir.resolve("build/generated/test/boundary/persistence/cassandra/$GENERATED_FILE_NAME")).contains("@Generated")

        where:
        scenario << Scenario.Java.all()
        expected = new Testresult.Java.Success("LazyvalCassandraCodecs.java")
    }

    void "Codec generated at correct default location when no override is given"(){
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencyDriverCore)

        when:
        testkitJava.run(projectDir, scenario)

        then: 'file is generated at correct location using base-package with generator-default'
        projectDir.resolve("build/generated/test/boundary/persistence/cassandra/$GENERATED_FILE_NAME").toFile().exists()
    }

    void "Package override by generator works as expected"(){
        given:
        def scenario = Scenario.Java.quantity().withDependencies(dependencyDriverCore)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.cassandra.package", "test.custom")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and: 'file is at correct package'
        projectDir.resolve("build/generated/test/custom/$GENERATED_FILE_NAME").toFile().exists()
    }

    void "OrderDate wrapping LocalDate generates a valid codec"(){
        given:
        def scenario = Scenario.Java.orderDate()
                .withDependencies(dependencyDriverCore)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)
    }

    void "Quarkus Registration generated when Cassandra-Quarkus-Extension is available"(){
        given:
        def scenario = Scenario.Java.orderDate()
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
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME, "LazyvalCassandraCodecRegistrar.java")
    }

    void "single valid user codec is appended to all()"() {
        given:
        def scenario = Scenario.Java.of(
                "valid-codec",
                "scenarios/java/Quantity.java",
                "scenarios/cassandracodecs/ValidCassandraCodec.java")
                .withDependencies(dependencyDriverCore)
        scenario.withOption("lazyval.cassandra.codecs", "scenarios.cassandracodecs.ValidCassandraCodec")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and: 'generated file references the user codec'
        def generated = projectDir.resolve("build/generated/test/boundary/persistence/cassandra/$GENERATED_FILE_NAME").toFile().text
        generated.contains("new scenarios.cassandracodecs.ValidCassandraCodec()")
    }

    void "multiple valid user codecs are appended in declared order"() {
        given:
        def scenario = Scenario.Java.of(
                "multiple-valid",
                "scenarios/java/Quantity.java",
                "scenarios/cassandracodecs/ValidCassandraCodec.java",
                "scenarios/cassandracodecs/AnotherValidCassandraCodec.java")
                .withDependencies(dependencyDriverCore)
        scenario.withOption("lazyval.cassandra.codecs",
                "scenarios.cassandracodecs.ValidCassandraCodec,scenarios.cassandracodecs.AnotherValidCassandraCodec")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and:
        def generated = projectDir.resolve("build/generated/test/boundary/persistence/cassandra/$GENERATED_FILE_NAME").toFile().text
        def validIdx = generated.indexOf("new scenarios.cassandracodecs.ValidCassandraCodec()")
        def anotherIdx = generated.indexOf("new scenarios.cassandracodecs.AnotherValidCassandraCodec()")
        validIdx >= 0
        anotherIdx > validIdx
    }

    void "whitespace and empty segments in option are tolerated"() {
        given:
        def scenario = Scenario.Java.of(
                "whitespace-in-option",
                "scenarios/java/Quantity.java",
                "scenarios/cassandracodecs/ValidCassandraCodec.java",
                "scenarios/cassandracodecs/AnotherValidCassandraCodec.java")
                .withDependencies(dependencyDriverCore)
        scenario.withOption("lazyval.cassandra.codecs",
                " scenarios.cassandracodecs.ValidCassandraCodec , , scenarios.cassandracodecs.AnotherValidCassandraCodec ")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and:
        def generated = projectDir.resolve("build/generated/test/boundary/persistence/cassandra/$GENERATED_FILE_NAME").toFile().text
        generated.contains("new scenarios.cassandracodecs.ValidCassandraCodec()")
        generated.contains("new scenarios.cassandracodecs.AnotherValidCassandraCodec()")
    }

    void "missing class FQN fails the build"() {
        given:
        def scenario = Scenario.Java.quantity().withDependencies(dependencyDriverCore)
        scenario.withOption("lazyval.cassandra.codecs", "com.example.Missing")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("lazyval.cassandra.codecs") && it.contains("com.example.Missing") && it.contains("not found on compile classpath")
        }
    }

    void "class that does not implement TypeCodec fails the build"() {
        given:
        def scenario = Scenario.Java.of(
                "invalid-codec",
                "scenarios/java/Quantity.java",
                "scenarios/cassandracodecs/NotACassandraCodec.java")
                .withDependencies(dependencyDriverCore)
        scenario.withOption("lazyval.cassandra.codecs", "scenarios.cassandracodecs.NotACassandraCodec")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("scenarios.cassandracodecs.NotACassandraCodec") && it.contains("does not implement") && it.contains("TypeCodec")
        }
    }

    void "package-private codec in different package fails the build"() {
        given: 'codecs are generated at default location (test.boundary.persistence.cassandra), codec lives in scenarios.cassandracodecs'
        def scenario = Scenario.Java.of(
                "not-accessible",
                "scenarios/java/Quantity.java",
                "scenarios/cassandracodecs/NonPublicCassandraCodec.java")
                .withDependencies(dependencyDriverCore)
        scenario.withOption("lazyval.cassandra.codecs", "scenarios.cassandracodecs.NonPublicCassandraCodec")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("scenarios.cassandracodecs.NonPublicCassandraCodec") && it.contains("not accessible")
        }
    }

    void "package-private codec in same package as generated codecs succeeds"() {
        given: 'codecs class is generated into the codec package'
        def scenario = Scenario.Java.of(
                "accessible",
                "scenarios/java/Quantity.java",
                "scenarios/cassandracodecs/NonPublicCassandraCodec.java")
                .withDependencies(dependencyDriverCore)
        scenario.withOption("lazyval.cassandra.package", "scenarios.cassandracodecs")
        scenario.withOption("lazyval.cassandra.codecs", "scenarios.cassandracodecs.NonPublicCassandraCodec")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and:
        def generated = projectDir.resolve("build/generated/scenarios/cassandracodecs/$GENERATED_FILE_NAME").toFile().text
        generated.contains("new scenarios.cassandracodecs.NonPublicCassandraCodec()")
    }

    void "unconditionally inaccessible codec class fails the build"() {
        given: 'NonAccessibleCassandraCodec.Inner is a private static nested class - unreachable from anywhere'
        def scenario = Scenario.Java.of(
                "inner-private-not-accessible",
                "scenarios/java/Quantity.java",
                "scenarios/cassandracodecs/NonAccessibleCassandraCodec.java")
                .withDependencies(dependencyDriverCore)
        scenario.withOption("lazyval.cassandra.codecs", "scenarios.cassandracodecs.NonAccessibleCassandraCodec.Inner")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("scenarios.cassandracodecs.NonAccessibleCassandraCodec.Inner") && it.contains("not accessible")
        }
    }

    void "codec without no-arg constructor fails the build"() {
        given:
        def scenario = Scenario.Java.of(
                "missing-no-arg-ctor",
                "scenarios/java/Quantity.java",
                "scenarios/cassandracodecs/NoNoArgCassandraCodec.java")
                .withDependencies(dependencyDriverCore)
        scenario.withOption("lazyval.cassandra.codecs", "scenarios.cassandracodecs.NoNoArgCassandraCodec")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("scenarios.cassandracodecs.NoNoArgCassandraCodec") && it.contains("no-arg constructor")
        }
    }

    void "two invalid FQNs report both errors in one build"() {
        given:
        def scenario = Scenario.Java.of(
                "invalid-fqns-reported",
                "scenarios/java/Quantity.java",
                "scenarios/cassandracodecs/NotACassandraCodec.java",
                "scenarios/cassandracodecs/NoNoArgCassandraCodec.java")
                .withDependencies(dependencyDriverCore)
        scenario.withOption("lazyval.cassandra.codecs",
                "scenarios.cassandracodecs.NotACassandraCodec,scenarios.cassandracodecs.NoNoArgCassandraCodec")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure

        and: 'NotACassandraCodec error is reported'
        failure.errors().any {
            it.contains("scenarios.cassandracodecs.NotACassandraCodec") && it.contains("does not implement")
        }
        and: 'NoNoArgCassandraCodec error is reported'
        failure.errors().any {
            it.contains("scenarios.cassandracodecs.NoNoArgCassandraCodec") && it.contains("no-arg constructor")
        }
    }
}
