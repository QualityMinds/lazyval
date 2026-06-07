package com.qualityminds.lazyval.ksp

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.*

import java.nio.file.Path

@Title("KSP Generator Integration - MongoDB Codec")
class KspMongoCodecIT extends Specification {

    public static final Dependency dependencyBson = new Dependency("org.mongodb", "bson", "5.6.5")
    public static final Dependency dependencyMongoDriverCore = new Dependency("org.mongodb", "mongodb-driver-core", "5.6.5")
    public static final Dependency dependencyQuarkusMongo = new Dependency("io.quarkus", "quarkus-mongodb-client", "3.34.5")
    public static final Dependency dependencyCdi = new Dependency("jakarta.enterprise", "jakarta.enterprise.cdi-api", "4.1.0")
    public static final Dependency dependencyInject = new Dependency("jakarta.inject", "jakarta.inject-api", "2.0.1")
    public static final Dependency dependencyQuarkusArc = new Dependency("io.quarkus.arc", "arc", "3.34.5")

    @TempDir()
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    @Unroll("#scenario.name() compiles and generated #expected.generatedFiles()")
    void "MongoDB Codec with all Scenarios"() {
        given:
        scenario.withDependencies(dependencyBson)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario << Scenario.Kotlin.all()
        expected = new Testresult.Kotlin.Success("LazyvalMongoCodecs.kt")
    }

    void "Codec generated at correct default location when no override is given"() {
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(dependencyBson)

        when:
        testkitKotlin.run(projectDir, scenario)

        then: 'file is generated at correct location using base-package with generator-default'
        projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/mongodb/LazyvalMongoCodecs.kt").toFile().exists()
    }

    void "Package override by generator works as expected"() {
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(dependencyBson)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.mongodb.package", "test.custom")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Kotlin.Success(["LazyvalMongoCodecs.kt"])

        and: 'file is at correct package'
        projectDir.resolve("build/generated/ksp/kotlin/test/custom/LazyvalMongoCodecs.kt").toFile().exists()
    }

    void "OrderDate wrapping LocalDate generates a valid codec"() {
        given:
        def scenario = Scenario.Kotlin.orderDate().withDependencies(dependencyBson)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success("LazyvalMongoCodecs.kt")
    }

    void "Quarkus Registrar generated when quarkus-mongodb-client is available"() {
        given:
        def scenario = Scenario.Kotlin.orderDate()
                .withDependencies(
                        dependencyBson,
                        dependencyMongoDriverCore,
                        dependencyQuarkusMongo,
                        dependencyCdi,
                        dependencyInject,
                        dependencyQuarkusArc)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success("LazyvalMongoCodecs.kt", "LazyvalMongoCodecRegistrar.kt")
    }

    void "Quarkus Registrar can be disabled via option"() {
        given:
        def scenario = Scenario.Kotlin.orderDate()
                .withDependencies(
                        dependencyBson,
                        dependencyMongoDriverCore,
                        dependencyQuarkusMongo,
                        dependencyCdi,
                        dependencyInject,
                        dependencyQuarkusArc)
        scenario.withOption("lazyval.mongodb.quarkus.register", "false")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success("LazyvalMongoCodecs.kt")
    }

    void "single valid user codec is added to userCodecs"() {
        given:
        def scenario = Scenario.Kotlin.of("scenarios/kotlin/Quantity.kt", "scenarios/mongocodecs/ValidMongoCodec.kt")
                .withDependencies(dependencyBson)
        scenario.withOption("lazyval.mongodb.codecs", "scenarios.mongocodecs.ValidMongoCodec")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success("LazyvalMongoCodecs.kt")

        and: 'generated file references the user codec'
        def generated = projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/mongodb/LazyvalMongoCodecs.kt").toFile().text
        generated.contains("scenarios.mongocodecs.ValidMongoCodec()")
    }

    void "multiple valid user codecs are all included in declared order"() {
        given:
        def scenario = Scenario.Kotlin.of("scenarios/kotlin/Quantity.kt",
                "scenarios/mongocodecs/ValidMongoCodec.kt",
                "scenarios/mongocodecs/AnotherValidMongoCodec.kt")
                .withDependencies(dependencyBson)
        scenario.withOption("lazyval.mongodb.codecs",
                "scenarios.mongocodecs.ValidMongoCodec,scenarios.mongocodecs.AnotherValidMongoCodec")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success("LazyvalMongoCodecs.kt")

        and:
        def generated = projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/mongodb/LazyvalMongoCodecs.kt").toFile().text
        def validIdx = generated.indexOf("scenarios.mongocodecs.ValidMongoCodec()")
        def anotherIdx = generated.indexOf("scenarios.mongocodecs.AnotherValidMongoCodec()")
        validIdx >= 0
        anotherIdx > validIdx
    }

    void "whitespace and empty segments in option are tolerated"() {
        given:
        def scenario = Scenario.Kotlin.of("scenarios/kotlin/Quantity.kt",
                "scenarios/mongocodecs/ValidMongoCodec.kt",
                "scenarios/mongocodecs/AnotherValidMongoCodec.kt")
                .withDependencies(dependencyBson)
        scenario.withOption("lazyval.mongodb.codecs",
                " scenarios.mongocodecs.ValidMongoCodec , , scenarios.mongocodecs.AnotherValidMongoCodec ")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success("LazyvalMongoCodecs.kt")

        and:
        def generated = projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/mongodb/LazyvalMongoCodecs.kt").toFile().text
        generated.contains("scenarios.mongocodecs.ValidMongoCodec()")
        generated.contains("scenarios.mongocodecs.AnotherValidMongoCodec()")
    }

    void "missing class FQN fails the build"() {
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(dependencyBson)
        scenario.withOption("lazyval.mongodb.codecs", "com.example.Missing")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Failure
        def failure = result as Testresult.Kotlin.Failure
        failure.errors().any {
            it.contains("lazyval.mongodb.codecs") && it.contains("com.example.Missing") && it.contains("not found on compile classpath")
        }
    }

    void "class that does not implement Codec fails the build"() {
        given:
        def scenario = Scenario.Kotlin.of("scenarios/kotlin/Quantity.kt", "scenarios/mongocodecs/NotAMongoCodec.kt")
                .withDependencies(dependencyBson)
        scenario.withOption("lazyval.mongodb.codecs", "scenarios.mongocodecs.NotAMongoCodec")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Failure
        def failure = result as Testresult.Kotlin.Failure
        failure.errors().any {
            it.contains("scenarios.mongocodecs.NotAMongoCodec") && it.contains("does not implement") && it.contains("Codec")
        }
    }

    void "internal codec from the current module succeeds"() {
        given: 'NonPublicMongoCodec is declared `internal` and lives in the same KSP-processed module'
        def scenario = Scenario.Kotlin.of("scenarios/kotlin/Quantity.kt", "scenarios/mongocodecs/NonPublicMongoCodec.kt")
                .withDependencies(dependencyBson)
        scenario.withOption("lazyval.mongodb.codecs", "scenarios.mongocodecs.NonPublicMongoCodec")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success("LazyvalMongoCodecs.kt")

        and:
        def generated = projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/mongodb/LazyvalMongoCodecs.kt").toFile().text
        generated.contains("scenarios.mongocodecs.NonPublicMongoCodec()")
    }

    void "unconditionally inaccessible codec class fails the build"() {
        given: 'NonAccessibleMongoCodec is a top-level `private` (file-scoped) class'
        def scenario = Scenario.Kotlin.of("scenarios/kotlin/Quantity.kt", "scenarios/mongocodecs/NonAccessibleMongoCodec.kt")
                .withDependencies(dependencyBson)
        scenario.withOption("lazyval.mongodb.codecs", "scenarios.mongocodecs.NonAccessibleMongoCodec")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Failure
        def failure = result as Testresult.Kotlin.Failure
        failure.errors().any {
            it.contains("scenarios.mongocodecs.NonAccessibleMongoCodec") && it.contains("not accessible")
        }
    }

    void "codec without no-arg constructor fails the build"() {
        given:
        def scenario = Scenario.Kotlin.of("scenarios/kotlin/Quantity.kt", "scenarios/mongocodecs/NoNoArgMongoCodec.kt")
                .withDependencies(dependencyBson)
        scenario.withOption("lazyval.mongodb.codecs", "scenarios.mongocodecs.NoNoArgMongoCodec")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Failure
        def failure = result as Testresult.Kotlin.Failure
        failure.errors().any {
            it.contains("scenarios.mongocodecs.NoNoArgMongoCodec") && it.contains("no-arg constructor")
        }
    }

    void "two invalid FQNs report both errors in one build"() {
        given:
        def scenario = Scenario.Kotlin.of("scenarios/kotlin/Quantity.kt",
                "scenarios/mongocodecs/NotAMongoCodec.kt",
                "scenarios/mongocodecs/NoNoArgMongoCodec.kt")
                .withDependencies(dependencyBson)
        scenario.withOption("lazyval.mongodb.codecs",
                "scenarios.mongocodecs.NotAMongoCodec,scenarios.mongocodecs.NoNoArgMongoCodec")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Failure
        def failure = result as Testresult.Kotlin.Failure

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
