package com.qualityminds.lazyval.processor

import com.qualityminds.lazyval.testkit.Approval
import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.*

import java.nio.file.Path

@Title("Generator Integration - MongoDB Codec")
class ApMongoCodecIT extends Specification {

    public static final Dependency dependencyBson = new Dependency("org.mongodb", "bson", "5.6.5")
    public static final Dependency dependencyMongoDriverCore = new Dependency("org.mongodb", "mongodb-driver-core", "5.6.5")
    public static final Dependency dependencyQuarkusMongo = new Dependency("io.quarkus", "quarkus-mongodb-client", "3.34.5")
    public static final Dependency dependencyCdi = new Dependency("jakarta.enterprise", "jakarta.enterprise.cdi-api", "4.1.0")
    public static final Dependency dependencyInject = new Dependency("jakarta.inject", "jakarta.inject-api", "2.0.1")
    public static final Dependency dependencyQuarkusArc = new Dependency("io.quarkus.arc", "arc", "3.34.5")

    private static final String GENERATED_FILE_NAME = "LazyvalMongoCodecs.java"

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    void "MongoDB Codec with combined Scenarios"() {
        given: 'a compiler run with all sources'
        def scenario = Scenario.Java.combined().withDependencies(dependencyBson)

        and: 'a defined approval for the generated codecs class'
        List<Approval> approvals = [
                Approval.JavaSource.at("test/boundary/persistence/mongodb/$GENERATED_FILE_NAME",
                        "approvals/mongocodec/$GENERATED_FILE_NAME")
        ]

        when:
        def result = testkitJava.run(projectDir, scenario, approvals)

        then:
        result == Testresult.Java.Approved.of(approvals)
    }

    void "Codec generated at correct default location when no override is given"() {
        given:
        def scenario = Scenario.Java.quantity().withDependencies(dependencyBson)

        when:
        testkitJava.run(projectDir, scenario)

        then: 'file is generated at correct location using base-package with generator-default'
        testkitJava.generatedSourcePath(projectDir, "test/boundary/persistence/mongodb/LazyvalMongoCodecs.java").toFile().exists()
    }

    void "Package override by generator works as expected"() {
        given:
        def scenario = Scenario.Java.quantity().withDependencies(dependencyBson)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.mongodb.package", "test.custom")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and: 'file is at correct package'
        testkitJava.generatedSourcePath(projectDir, "test/custom/LazyvalMongoCodecs.java").toFile().exists()
    }

    void "OrderDate wrapping LocalDate generates a valid codec"() {
        given:
        def scenario = Scenario.Java.orderDate().withDependencies(dependencyBson)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)
    }

    void "Quarkus Registrar generated when quarkus-mongodb-client is available"() {
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(
                        dependencyBson,
                        dependencyMongoDriverCore,
                        dependencyQuarkusMongo,
                        dependencyCdi,
                        dependencyInject,
                        dependencyQuarkusArc)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME, "LazyvalMongoCodecRegistrar.java")
    }

    void "Quarkus Registrar can be disabled via option"() {
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(
                        dependencyBson,
                        dependencyMongoDriverCore,
                        dependencyQuarkusMongo,
                        dependencyCdi,
                        dependencyInject,
                        dependencyQuarkusArc)
        scenario.withOption("lazyval.mongodb.quarkus.register", "false")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)
    }

    void "single valid user codec is added to userCodecs"() {
        given:
        def scenario = Scenario.Java.of(
                "valid-codec",
                "scenarios/java/Quantity.java",
                "scenarios/mongocodecs/ValidMongoCodec.java")
                .withDependencies(dependencyBson)
        scenario.withOption("lazyval.mongodb.codecs", "scenarios.mongocodecs.ValidMongoCodec")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and: 'generated file references the user codec'
        def generated = testkitJava.generatedSourcePath(projectDir, "test/boundary/persistence/mongodb/LazyvalMongoCodecs.java").toFile().text
        generated.contains("new scenarios.mongocodecs.ValidMongoCodec()")
    }

    void "multiple valid user codecs are all included in declared order"() {
        given:
        def scenario = Scenario.Java.of(
                "multiple-valid-codecs",
                "scenarios/java/Quantity.java",
                "scenarios/mongocodecs/ValidMongoCodec.java",
                "scenarios/mongocodecs/AnotherValidMongoCodec.java")
                .withDependencies(dependencyBson)
        scenario.withOption("lazyval.mongodb.codecs",
                "scenarios.mongocodecs.ValidMongoCodec,scenarios.mongocodecs.AnotherValidMongoCodec")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and:
        def generated = testkitJava.generatedSourcePath(projectDir, "test/boundary/persistence/mongodb/LazyvalMongoCodecs.java").toFile().text
        def validIdx = generated.indexOf("new scenarios.mongocodecs.ValidMongoCodec()")
        def anotherIdx = generated.indexOf("new scenarios.mongocodecs.AnotherValidMongoCodec()")
        validIdx >= 0
        anotherIdx > validIdx
    }

    void "whitespace and empty segments in option are tolerated"() {
        given:
        def scenario = Scenario.Java.of(
                "whitespace-in-option",
                "scenarios/java/Quantity.java",
                "scenarios/mongocodecs/ValidMongoCodec.java",
                "scenarios/mongocodecs/AnotherValidMongoCodec.java")
                .withDependencies(dependencyBson)
        scenario.withOption("lazyval.mongodb.codecs",
                " scenarios.mongocodecs.ValidMongoCodec , , scenarios.mongocodecs.AnotherValidMongoCodec ")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and:
        def generated = testkitJava.generatedSourcePath(projectDir, "test/boundary/persistence/mongodb/LazyvalMongoCodecs.java").toFile().text
        generated.contains("new scenarios.mongocodecs.ValidMongoCodec()")
        generated.contains("new scenarios.mongocodecs.AnotherValidMongoCodec()")
    }

    void "missing class FQN fails the build"() {
        given:
        def scenario = Scenario.Java.quantity().withDependencies(dependencyBson)
        scenario.withOption("lazyval.mongodb.codecs", "com.example.Missing")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("lazyval.mongodb.codecs") && it.contains("com.example.Missing") && it.contains("not found on compile classpath")
        }
    }

    void "class that does not implement Codec fails the build"() {
        given:
        def scenario = Scenario.Java.of(
                "not-implementing-interface",
                "scenarios/java/Quantity.java",
                "scenarios/mongocodecs/NotAMongoCodec.java")
                .withDependencies(dependencyBson)
        scenario.withOption("lazyval.mongodb.codecs", "scenarios.mongocodecs.NotAMongoCodec")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("scenarios.mongocodecs.NotAMongoCodec") && it.contains("does not implement") && it.contains("Codec")
        }
    }

    void "package-private codec in different package fails the build"() {
        given: 'codecs are generated at default location (test.boundary.persistence.mongodb), codec lives in scenarios.mongocodecs'
        def scenario = Scenario.Java.of(
                "valid-codec",
                "scenarios/java/Quantity.java",
                "scenarios/mongocodecs/NonPublicMongoCodec.java")
                .withDependencies(dependencyBson)
        scenario.withOption("lazyval.mongodb.codecs", "scenarios.mongocodecs.NonPublicMongoCodec")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("scenarios.mongocodecs.NonPublicMongoCodec") && it.contains("not accessible")
        }
    }

    void "package-private codec in same package as generated codecs succeeds"() {
        given: 'codecs class is generated into the codec package'
        def scenario = Scenario.Java.of(
                "valid-codec",
                "scenarios/java/Quantity.java",
                "scenarios/mongocodecs/NonPublicMongoCodec.java")
                .withDependencies(dependencyBson)
        scenario.withOption("lazyval.mongodb.package", "scenarios.mongocodecs")
        scenario.withOption("lazyval.mongodb.codecs", "scenarios.mongocodecs.NonPublicMongoCodec")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and:
        def generated = testkitJava.generatedSourcePath(projectDir, "scenarios/mongocodecs/LazyvalMongoCodecs.java").toFile().text
        generated.contains("new scenarios.mongocodecs.NonPublicMongoCodec()")
    }

    void "unconditionally inaccessible codec class fails the build"() {
        given: 'NonAccessibleMongoCodec.Inner is a private static nested class - unreachable from anywhere'
        def scenario = Scenario.Java.of(
                "inner-private-not-accessible",
                "scenarios/java/Quantity.java",
                "scenarios/mongocodecs/NonAccessibleMongoCodec.java")
                .withDependencies(dependencyBson)
        scenario.withOption("lazyval.mongodb.codecs", "scenarios.mongocodecs.NonAccessibleMongoCodec.Inner")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("scenarios.mongocodecs.NonAccessibleMongoCodec.Inner") && it.contains("not accessible")
        }
    }

    void "codec without no-arg constructor fails the build"() {
        given:
        def scenario = Scenario.Java.of(
                "missing-no-arg-ctor",
                "scenarios/java/Quantity.java",
                "scenarios/mongocodecs/NoNoArgMongoCodec.java")
                .withDependencies(dependencyBson)
        scenario.withOption("lazyval.mongodb.codecs", "scenarios.mongocodecs.NoNoArgMongoCodec")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("scenarios.mongocodecs.NoNoArgMongoCodec") && it.contains("no-arg constructor")
        }
    }

    void "two invalid FQNs report both errors in one build"() {
        given:
        def scenario = Scenario.Java.of(
                "invalid-fqns-reported",
                "scenarios/java/Quantity.java",
                "scenarios/mongocodecs/NotAMongoCodec.java",
                "scenarios/mongocodecs/NoNoArgMongoCodec.java")
                .withDependencies(dependencyBson)
        scenario.withOption("lazyval.mongodb.codecs",
                "scenarios.mongocodecs.NotAMongoCodec,scenarios.mongocodecs.NoNoArgMongoCodec")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure

        and: 'NotAMongoCodec error is reported'
        failure.errors().any {
            it.contains("scenarios.mongocodecs.NotAMongoCodec") && it.contains("does not implement")
        }
        and: 'NoNoArgMongoCodec error is reported'
        failure.errors().any {
            it.contains("scenarios.mongocodecs.NoNoArgMongoCodec") && it.contains("no-arg constructor")
        }
    }
}
