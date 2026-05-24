package com.qualityminds.lazyval.ksp

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.*

import java.nio.file.Path

@Title("Generator Integration - Spring Data MongoDB (KSP)")
class KspSpringDataMongoIT extends Specification {

    public static final Dependency dependencySpringDataMongo = new Dependency("org.springframework.data", "spring-data-mongodb", "4.4.6")
    public static final Dependency dependencySpringDataCassandra = new Dependency("org.springframework.data", "spring-data-cassandra", "4.4.6")
    public static final Dependency dependencySpringDataCommons = new Dependency("org.springframework.data", "spring-data-commons", "3.4.6")
    public static final Dependency dependencySpringCore = new Dependency("org.springframework", "spring-core", "6.2.7")
    public static final Dependency dependencySpringBeans = new Dependency("org.springframework", "spring-beans", "6.2.7")
    public static final Dependency dependencySpringContext = new Dependency("org.springframework", "spring-context", "6.2.7")

    @TempDir()
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    @Unroll("#scenario.name() compiles and generates LazyvalSpringDataConfiguration.kt for MongoDB")
    void "all Scenarios compile and generate the configuration"() {
        given:
        scenario.withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success("LazyvalSpringDataConfiguration.kt")

        where:
        scenario << Scenario.Kotlin.all()
    }

    void "single valid user converter is appended to mongoCustomConversions"() {
        given:
        def scenario = Scenario.Kotlin.of("scenarios/kotlin/Quantity.kt", "scenarios/converters/ValidConverter.kt")
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.spring_data.mongo.converters", "scenarios.converters.ValidConverter")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success("LazyvalSpringDataConfiguration.kt")

        and:
        def generated = projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/LazyvalSpringDataConfiguration.kt").toFile().text
        generated.contains("scenarios.converters.ValidConverter()")
        generated.contains("// user-supplied via lazyval.spring_data.mongo.converters:")
    }

    void "multiple valid user converters are all appended in declared order"() {
        given:
        def scenario = Scenario.Kotlin.of("scenarios/kotlin/Quantity.kt",
                "scenarios/converters/ValidConverter.kt",
                "scenarios/converters/AnotherValidConverter.kt")
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.spring_data.mongo.converters",
                "scenarios.converters.ValidConverter,scenarios.converters.AnotherValidConverter")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success("LazyvalSpringDataConfiguration.kt")

        and:
        def generated = projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/LazyvalSpringDataConfiguration.kt").toFile().text
        def validIdx = generated.indexOf("scenarios.converters.ValidConverter()")
        def anotherIdx = generated.indexOf("scenarios.converters.AnotherValidConverter()")
        validIdx >= 0
        anotherIdx > validIdx
    }

    void "whitespace and empty segments in option are tolerated"() {
        given:
        def scenario = Scenario.Kotlin.of("scenarios/kotlin/Quantity.kt",
                "scenarios/converters/ValidConverter.kt",
                "scenarios/converters/AnotherValidConverter.kt")
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.spring_data.mongo.converters",
                " scenarios.converters.ValidConverter , , scenarios.converters.AnotherValidConverter ")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success("LazyvalSpringDataConfiguration.kt")

        and:
        def generated = projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/LazyvalSpringDataConfiguration.kt").toFile().text
        generated.contains("scenarios.converters.ValidConverter()")
        generated.contains("scenarios.converters.AnotherValidConverter()")
    }

    void "missing class FQN fails the build"() {
        given:
        def scenario = Scenario.Kotlin.quantity()
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.spring_data.mongo.converters", "com.example.Missing")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Failure
        def failure = result as Testresult.Kotlin.Failure
        failure.errors().any {
            it.contains("lazyval.spring_data.mongo.converters") && it.contains("com.example.Missing") && it.contains("not found on compile classpath")
        }
    }

    void "class that does not implement Converter fails the build"() {
        given:
        def scenario = Scenario.Kotlin.of("scenarios/kotlin/Quantity.kt", "scenarios/converters/NotAConverter.kt")
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.spring_data.mongo.converters", "scenarios.converters.NotAConverter")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Failure
        def failure = result as Testresult.Kotlin.Failure
        failure.errors().any {
            it.contains("scenarios.converters.NotAConverter") && it.contains("does not implement") && it.contains("Converter")
        }
    }

    void "internal converter from the current module succeeds"() {
        given: 'NonPublicConverter is declared `internal` and lives in the same KSP-processed module'
        def scenario = Scenario.Kotlin.of("scenarios/kotlin/Quantity.kt", "scenarios/converters/NonPublicConverter.kt")
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.spring_data.mongo.converters", "scenarios.converters.NonPublicConverter")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success("LazyvalSpringDataConfiguration.kt")

        and:
        def generated = projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/LazyvalSpringDataConfiguration.kt").toFile().text
        generated.contains("scenarios.converters.NonPublicConverter()")
    }

    void "unconditionally inaccessible converter class fails the build"() {
        given: 'NonAccessibleConverter is a top-level `private` (file-scoped) class'
        def scenario = Scenario.Kotlin.of("scenarios/kotlin/Quantity.kt", "scenarios/converters/NonAccessibleConverter.kt")
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.spring_data.mongo.converters", "scenarios.converters.NonAccessibleConverter")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Failure
        def failure = result as Testresult.Kotlin.Failure
        failure.errors().any {
            it.contains("scenarios.converters.NonAccessibleConverter") && it.contains("not accessible")
        }
    }

    void "converter without @ReadingConverter or @WritingConverter fails the build"() {
        given:
        def scenario = Scenario.Kotlin.of("scenarios/kotlin/Quantity.kt", "scenarios/converters/UnannotatedConverter.kt")
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.spring_data.mongo.converters", "scenarios.converters.UnannotatedConverter")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Failure
        def failure = result as Testresult.Kotlin.Failure
        failure.errors().any {
            it.contains("scenarios.converters.UnannotatedConverter") && it.contains("@ReadingConverter") && it.contains("@WritingConverter")
        }
    }

    void "converter without no-arg constructor fails the build"() {
        given:
        def scenario = Scenario.Kotlin.of("scenarios/kotlin/Quantity.kt", "scenarios/converters/NoNoArgConverter.kt")
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.spring_data.mongo.converters", "scenarios.converters.NoNoArgConverter")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Failure
        def failure = result as Testresult.Kotlin.Failure
        failure.errors().any {
            it.contains("scenarios.converters.NoNoArgConverter") && it.contains("no-arg constructor")
        }
    }

    void "two invalid FQNs report both errors in one build"() {
        given:
        def scenario = Scenario.Kotlin.of("scenarios/kotlin/Quantity.kt",
                "scenarios/converters/NotAConverter.kt",
                "scenarios/converters/NoNoArgConverter.kt")
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.spring_data.mongo.converters",
                "scenarios.converters.NotAConverter,scenarios.converters.NoNoArgConverter")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Failure
        def failure = result as Testresult.Kotlin.Failure

        and: 'NotAConverter error is reported'
        failure.errors().any {
            it.contains("scenarios.converters.NotAConverter") && it.contains("does not implement")
        }
        and: 'NoNoArgConverter error is reported'
        failure.errors().any {
            it.contains("scenarios.converters.NoNoArgConverter") && it.contains("no-arg constructor")
        }
    }

    void "option set but spring-data-mongodb missing produces warning, not failure"() {
        given: 'only Cassandra is on the classpath but Mongo option is set'
        def scenario = Scenario.Kotlin.quantity()
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.spring_data.mongo.converters", "com.example.Whatever")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.SuccessWithWarnings
        def success = result as Testresult.Kotlin.SuccessWithWarnings
        success.generatedFiles().contains("LazyvalSpringDataConfiguration.kt")

        and:
        success.warnings().any {
            it.contains("lazyval.spring_data.mongo.converters") && it.contains("MongoDB") && it.contains("ignored")
        }

        and: 'no mongo bean method is generated'
        def generated = projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/LazyvalSpringDataConfiguration.kt").toFile().text
        !generated.contains("mongoCustomConversions")
    }

    void "Cassandra and Mongo options are independent"() {
        given: 'both stores on classpath, each with its own user converter'
        def scenario = Scenario.Kotlin.of("scenarios/kotlin/Quantity.kt",
                "scenarios/converters/ValidConverter.kt",
                "scenarios/converters/AnotherValidConverter.kt")
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.spring_data.cassandra.converters", "scenarios.converters.ValidConverter")
        scenario.withOption("lazyval.spring_data.mongo.converters", "scenarios.converters.AnotherValidConverter")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success("LazyvalSpringDataConfiguration.kt")

        and: 'cassandra method contains only ValidConverter; mongo method contains only AnotherValidConverter'
        def generated = projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/LazyvalSpringDataConfiguration.kt").toFile().text
        def cassandraStart = generated.indexOf("cassandraCustomConversions")
        def mongoStart = generated.indexOf("mongoCustomConversions")
        def cassandraSection = generated.substring(cassandraStart, mongoStart)
        def mongoSection = generated.substring(mongoStart)
        cassandraSection.contains("scenarios.converters.ValidConverter()")
        !cassandraSection.contains("AnotherValidConverter")
        mongoSection.contains("scenarios.converters.AnotherValidConverter()")
        !mongoSection.contains("scenarios.converters.ValidConverter()")
    }
}
