package com.qualityminds.lazyval.ksp

import com.qualityminds.lazyval.testkit.Approval
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

@Title("KSP Generator Integration - Spring Data Cassandra")
class KspSpringDataCassandraIT extends Specification {

    public static final Dependency dependencyJakartaAnnotations = new Dependency("jakarta.annotation", "jakarta.annotation-api", "3.0.0")
    public static final Dependency dependencySpringDataCassandra = new Dependency("org.springframework.data", "spring-data-cassandra", "4.4.6")
    public static final Dependency dependencySpringDataMongo = new Dependency("org.springframework.data", "spring-data-mongodb", "4.4.6")
    public static final Dependency dependencySpringDataCommons = new Dependency("org.springframework.data", "spring-data-commons", "3.4.6")
    public static final Dependency dependencySpringCore = new Dependency("org.springframework", "spring-core", "6.2.7")
    public static final Dependency dependencySpringBeans = new Dependency("org.springframework", "spring-beans", "6.2.7")
    public static final Dependency dependencySpringContext = new Dependency("org.springframework", "spring-context", "6.2.7")

    private static final String GENERATED_FILE_NAME = "LazyvalSpringDataConfiguration.kt"

    @TempDir()
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    void "Spring Data Cassandra with combined Scenarios"() {
        given: 'a compiler run with all sources'
        def scenario = Scenario.Kotlin.combined()
                .withDependencies(
                        dependencySpringDataCassandra,
                        dependencySpringDataCommons,
                        dependencySpringCore,
                        dependencySpringBeans,
                        dependencySpringContext,
                        dependencyJakartaAnnotations)

        and: 'a defined approval for the generated configuration'
        List<Approval.ForKotlin> approvals = [
                Approval.KotlinSource.at("test/boundary/persistence/$GENERATED_FILE_NAME",
                        "approvals/springdata/cassandra/$GENERATED_FILE_NAME")
        ]

        when:
        def result = testkitKotlin.run(projectDir, scenario, approvals)

        then:
        result == Testresult.Kotlin.Approved.of(approvals)
    }

    void "Nothing generated when no CustomConversions is on classpath"() {
        given:
        def scenario = Scenario.Kotlin.quantity()
                .withDependencies(dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.NothingGenerated()
    }

    void "Converters generated at correct default location when no override is given"() {
        given:
        def scenario = Scenario.Kotlin.quantity()
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)

        when:
        testkitKotlin.run(projectDir, scenario)

        then: 'single configuration file is generated at correct location using base-package with generator-default'
        testkitKotlin.generatedKotlinSourcePath(projectDir, "test/boundary/persistence/LazyvalSpringDataConfiguration.kt").toFile().exists()
    }

    void "Package override by generator works as expected"() {
        given:
        def scenario = Scenario.Kotlin.quantity()
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.springdata.package", "test.custom")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME)

        and: 'file is at correct package'
        testkitKotlin.generatedKotlinSourcePath(projectDir, "test/custom/LazyvalSpringDataConfiguration.kt").toFile().exists()
    }

    void "single valid user converter is appended to cassandraCustomConversions"() {
        given:
        def scenario = Scenario.Kotlin.of(
                "one-valid",
                "scenarios/kotlin/Quantity.kt",
                "scenarios/converters/ValidConverter.kt")
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters", "scenarios.converters.ValidConverter")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME)

        and:
        def generated = testkitKotlin.generatedKotlinSourcePath(projectDir, "test/boundary/persistence/LazyvalSpringDataConfiguration.kt").toFile().text
        generated.contains("scenarios.converters.ValidConverter()")
        generated.contains("// user-supplied via lazyval.springdata.cassandra.converters:")
    }

    void "multiple valid user converters are all appended in declared order"() {
        given:
        def scenario = Scenario.Kotlin.of(
                "two-valid",
                "scenarios/kotlin/Quantity.kt",
                "scenarios/converters/ValidConverter.kt",
                "scenarios/converters/AnotherValidConverter.kt")
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters",
                "scenarios.converters.ValidConverter,scenarios.converters.AnotherValidConverter")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME)

        and:
        def generated = testkitKotlin.generatedKotlinSourcePath(projectDir, "test/boundary/persistence/LazyvalSpringDataConfiguration.kt").toFile().text
        def validIdx = generated.indexOf("scenarios.converters.ValidConverter()")
        def anotherIdx = generated.indexOf("scenarios.converters.AnotherValidConverter()")
        validIdx >= 0
        anotherIdx > validIdx
    }

    void "whitespace and empty segments in option are tolerated"() {
        given:
        def scenario = Scenario.Kotlin.of(
                "whitespace-in-option",
                "scenarios/kotlin/Quantity.kt",
                "scenarios/converters/ValidConverter.kt",
                "scenarios/converters/AnotherValidConverter.kt")
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters",
                " scenarios.converters.ValidConverter , , scenarios.converters.AnotherValidConverter ")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME)

        and:
        def generated = testkitKotlin.generatedKotlinSourcePath(projectDir, "test/boundary/persistence/LazyvalSpringDataConfiguration.kt").toFile().text
        generated.contains("scenarios.converters.ValidConverter()")
        generated.contains("scenarios.converters.AnotherValidConverter()")
    }

    void "missing class FQN fails the build"() {
        given:
        def scenario = Scenario.Kotlin.quantity()
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters", "com.example.Missing")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Failure
        def failure = result as Testresult.Kotlin.Failure
        failure.errors().any {
            it.contains("lazyval.springdata.cassandra.converters") && it.contains("com.example.Missing") && it.contains("not found on compile classpath")
        }
    }

    void "class that does not implement Converter fails the build"() {
        given:
        def scenario = Scenario.Kotlin.of(
                "not-a-converter",
                "scenarios/kotlin/Quantity.kt",
                "scenarios/converters/NotAConverter.kt")
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters", "scenarios.converters.NotAConverter")

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
        def scenario = Scenario.Kotlin.of(
                "internal-converter",
                "scenarios/kotlin/Quantity.kt",
                "scenarios/converters/NonPublicConverter.kt")
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters", "scenarios.converters.NonPublicConverter")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME)

        and:
        def generated = testkitKotlin.generatedKotlinSourcePath(projectDir, "test/boundary/persistence/LazyvalSpringDataConfiguration.kt").toFile().text
        generated.contains("scenarios.converters.NonPublicConverter()")
    }

    void "unconditionally inaccessible converter class fails the build"() {
        given: 'NonAccessibleConverter is a top-level `private` (file-scoped) class'
        def scenario = Scenario.Kotlin.of(
                "file-private",
                "scenarios/kotlin/Quantity.kt",
                "scenarios/converters/NonAccessibleConverter.kt")
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters", "scenarios.converters.NonAccessibleConverter")

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
        def scenario = Scenario.Kotlin.of(
                "missing-annotations",
                "scenarios/kotlin/Quantity.kt",
                "scenarios/converters/UnannotatedConverter.kt")
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters", "scenarios.converters.UnannotatedConverter")

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
        def scenario = Scenario.Kotlin.of(
                "missing-no-arg-ctor",
                "scenarios/kotlin/Quantity.kt",
                "scenarios/converters/NoNoArgConverter.kt")
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters", "scenarios.converters.NoNoArgConverter")

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
        def scenario = Scenario.Kotlin.of(
                "invalid-fqns-reported",
                "scenarios/kotlin/Quantity.kt",
                "scenarios/converters/NotAConverter.kt",
                "scenarios/converters/NoNoArgConverter.kt")
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters",
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

    void "option set but spring-data-cassandra missing produces warning, not failure"() {
        given: 'only MongoDB is on the classpath but Cassandra option is set'
        def scenario = Scenario.Kotlin.quantity()
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters", "com.example.Whatever")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.SuccessWithWarnings
        def success = result as Testresult.Kotlin.SuccessWithWarnings
        success.generatedFiles().contains(GENERATED_FILE_NAME)

        and:
        success.warnings().any {
            it.contains("lazyval.springdata.cassandra.converters") && it.contains("Cassandra") && it.contains("ignored")
        }

        and: 'no cassandra bean method is generated'
        def generated = testkitKotlin.generatedKotlinSourcePath(projectDir, "test/boundary/persistence/LazyvalSpringDataConfiguration.kt").toFile().text
        !generated.contains("cassandraCustomConversions")
    }

    void "Does not add '@Generated' when jakarta.annotations-api not on classpath"(){
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(
                dependencySpringDataCassandra,
                dependencySpringDataCommons,
                dependencySpringCore,
                dependencySpringBeans,
                dependencySpringContext)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME)

        and: 'doesnt contain @Generated'
        !Files.readString(testkitKotlin.generatedKotlinSourcePath(projectDir, "test/boundary/persistence/$GENERATED_FILE_NAME")).contains("@Generated")
    }
}
