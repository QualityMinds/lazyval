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

@Title("KSP Generator Integration - Spring Data R2DBC")
class KspSpringDataR2dbcIT extends Specification {

    public static final Dependency dependencyJakartaAnnotations = new Dependency("jakarta.annotation", "jakarta.annotation-api", "3.0.0")
    public static final Dependency dependencySpringDataR2dbc = new Dependency("org.springframework.data", "spring-data-r2dbc", "4.1.0")
    public static final Dependency dependencyR2dbcSpi = new Dependency("io.r2dbc", "r2dbc-spi", "1.0.0.RELEASE")
    public static final Dependency dependencySpringDataCommons = new Dependency("org.springframework.data", "spring-data-commons", "4.1.0")
    public static final Dependency dependencySpringDataCassandra = new Dependency("org.springframework.data", "spring-data-cassandra", "4.4.6")
    public static final Dependency dependencySpringCore = new Dependency("org.springframework", "spring-core", "7.0.8")
    public static final Dependency dependencySpringBeans = new Dependency("org.springframework", "spring-beans", "7.0.8")
    public static final Dependency dependencySpringContext = new Dependency("org.springframework", "spring-context", "7.0.8")

    private static final String GENERATED_FILE_NAME = "LazyvalSpringDataConfiguration.kt"

    @TempDir()
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    void "Spring Data R2DBC with combined Scenarios"() {
        given: 'a compiler run with all sources'
        def scenario = Scenario.Kotlin.combined()
                .withDependencies(
                        dependencySpringDataR2dbc,
                        dependencyR2dbcSpi,
                        dependencySpringDataCommons,
                        dependencySpringCore,
                        dependencySpringBeans,
                        dependencySpringContext,
                        dependencyJakartaAnnotations)

        and: 'a defined approval for the generated configuration'
        List<Approval.ForKotlin> approvals = [
                Approval.KotlinSource.at("test/boundary/persistence/$GENERATED_FILE_NAME",
                        "approvals/springdata/r2dbc/$GENERATED_FILE_NAME")
        ]

        when:
        def result = testkitKotlin.run(projectDir, scenario, approvals)

        then:
        result == Testresult.Kotlin.Approved.of(approvals)
    }

    void "single valid user converter is appended to r2dbcCustomConversions"() {
        given:
        def scenario = Scenario.Kotlin.of(
                "valid-converter",
                "scenarios/kotlin/Quantity.kt",
                "scenarios/converters/ValidConverter.kt")
                .withDependencies(dependencySpringDataR2dbc, dependencyR2dbcSpi, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext, dependencyJakartaAnnotations)
        scenario.withOption("lazyval.springdata.r2dbc.converters", "scenarios.converters.ValidConverter")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME)

        and:
        def generated = testkitKotlin.generatedKotlinSourcePath(projectDir, "test/boundary/persistence/$GENERATED_FILE_NAME").toFile().text
        generated.contains("scenarios.converters.ValidConverter()")
        generated.contains("// user-supplied via lazyval.springdata.r2dbc.converters:")
    }

    void "multiple valid user converters are all appended in declared order"() {
        given:
        def scenario = Scenario.Kotlin.of(
                "multiple-valid-converters",
                "scenarios/kotlin/Quantity.kt",
                "scenarios/converters/ValidConverter.kt",
                "scenarios/converters/AnotherValidConverter.kt")
                .withDependencies(dependencySpringDataR2dbc, dependencyR2dbcSpi, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext, dependencyJakartaAnnotations)
        scenario.withOption("lazyval.springdata.r2dbc.converters",
                "scenarios.converters.ValidConverter,scenarios.converters.AnotherValidConverter")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME)

        and:
        def generated = testkitKotlin.generatedKotlinSourcePath(projectDir, "test/boundary/persistence/$GENERATED_FILE_NAME").toFile().text
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
                .withDependencies(dependencySpringDataR2dbc, dependencyR2dbcSpi, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext, dependencyJakartaAnnotations)
        scenario.withOption("lazyval.springdata.r2dbc.converters",
                " scenarios.converters.ValidConverter , , scenarios.converters.AnotherValidConverter ")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME)

        and:
        def generated = testkitKotlin.generatedKotlinSourcePath(projectDir, "test/boundary/persistence/$GENERATED_FILE_NAME").toFile().text
        generated.contains("scenarios.converters.ValidConverter()")
        generated.contains("scenarios.converters.AnotherValidConverter()")
    }

    void "missing class FQN fails the build"() {
        given:
        def scenario = Scenario.Kotlin.quantity()
                .withDependencies(dependencySpringDataR2dbc, dependencyR2dbcSpi, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext, dependencyJakartaAnnotations)
        scenario.withOption("lazyval.springdata.r2dbc.converters", "com.example.Missing")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Failure
        def failure = result as Testresult.Kotlin.Failure
        failure.errors().any {
            it.contains("lazyval.springdata.r2dbc.converters") && it.contains("com.example.Missing") && it.contains("not found on compile classpath")
        }
    }

    void "class that does not implement Converter fails the build"() {
        given:
        def scenario = Scenario.Kotlin.of(
                "not-a-converter",
                "scenarios/kotlin/Quantity.kt",
                "scenarios/converters/NotAConverter.kt")
                .withDependencies(dependencySpringDataR2dbc, dependencyR2dbcSpi, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext, dependencyJakartaAnnotations)
        scenario.withOption("lazyval.springdata.r2dbc.converters", "scenarios.converters.NotAConverter")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Failure
        def failure = result as Testresult.Kotlin.Failure
        failure.errors().any {
            it.contains("scenarios.converters.NotAConverter") && it.contains("does not implement") && it.contains("Converter")
        }
    }

    void "converter without @ReadingConverter or @WritingConverter fails the build"() {
        given:
        def scenario = Scenario.Kotlin.of(
                "unannotated-converter",
                "scenarios/kotlin/Quantity.kt",
                "scenarios/converters/UnannotatedConverter.kt")
                .withDependencies(dependencySpringDataR2dbc, dependencyR2dbcSpi, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext, dependencyJakartaAnnotations)
        scenario.withOption("lazyval.springdata.r2dbc.converters", "scenarios.converters.UnannotatedConverter")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Failure
        def failure = result as Testresult.Kotlin.Failure
        failure.errors().any {
            it.contains("scenarios.converters.UnannotatedConverter") && it.contains("@ReadingConverter")
        }
    }

    void "option set but r2dbc Spring Data missing produces warning, not failure"() {
        given: 'only Cassandra is on the classpath but the R2DBC option is set'
        def scenario = Scenario.Kotlin.quantity()
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext, dependencyJakartaAnnotations)
        scenario.withOption("lazyval.springdata.r2dbc.converters", "com.example.Whatever")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.SuccessWithWarnings
        def success = result as Testresult.Kotlin.SuccessWithWarnings
        success.generatedFiles().contains(GENERATED_FILE_NAME)

        and:
        success.warnings().any {
            it.contains("lazyval.springdata.r2dbc.converters") && it.contains("R2DBC") && it.contains("ignored")
        }

        and: 'no r2dbcCustomConversions method is generated'
        def generated = testkitKotlin.generatedKotlinSourcePath(projectDir, "test/boundary/persistence/$GENERATED_FILE_NAME").toFile().text
        !generated.contains("r2dbcCustomConversions")
    }
}
