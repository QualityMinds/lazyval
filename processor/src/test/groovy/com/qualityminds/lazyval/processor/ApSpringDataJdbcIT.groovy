package com.qualityminds.lazyval.processor

import com.qualityminds.lazyval.testkit.Approval
import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title

import java.nio.file.Path

@Title("Generator Integration - Spring Data MongoDB")
class ApSpringDataJdbcIT extends Specification {

    public static final Dependency dependencySpringDataJdbc = new Dependency("org.springframework.data", "spring-data-jdbc", "4.1.0")
    // JdbcCustomConversions.of takes the relational Dialect supertype of JdbcDialect, and
    // MavenResolver does not resolve transitively, so spring-data-relational is explicit
    public static final Dependency dependencySpringDataRelational = new Dependency("org.springframework.data", "spring-data-relational", "4.1.0")
    public static final Dependency dependencySpringDataCommons = new Dependency("org.springframework.data", "spring-data-commons", "4.1.0")
    public static final Dependency dependencySpringDataCassandra = new Dependency("org.springframework.data", "spring-data-cassandra", "4.4.6")
    public static final Dependency dependencySpringCore = new Dependency("org.springframework", "spring-core", "7.0.8")
    public static final Dependency dependencySpringBeans = new Dependency("org.springframework", "spring-beans", "7.0.8")
    public static final Dependency dependencySpringContext = new Dependency("org.springframework", "spring-context", "7.0.8")

    private static final String GENERATED_FILE_NAME = "LazyvalSpringDataConfiguration.java"

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    void "Spring Data JDBC with combined Scenarios"() {
        given: 'a compiler run with all sources'
        def scenario = Scenario.Java.combined()
                .withDependencies(
                        dependencySpringDataJdbc,
                        dependencySpringDataRelational,
                        dependencySpringDataCommons,
                        dependencySpringCore,
                        dependencySpringBeans,
                        dependencySpringContext)

        and: 'a defined approval for the generated configuration'
        List<Approval.ForJava> approvals = [
                Approval.JavaSource.at("test/boundary/persistence/$GENERATED_FILE_NAME",
                        "approvals/springdata/jdbc/$GENERATED_FILE_NAME")
        ]

        when:
        def result = testkitJava.run(projectDir, scenario, approvals)

        then:
        result == Testresult.Java.Approved.of(approvals)
    }

    void "single valid user converter is appended to jdbcCustomConversions"() {
        given:
        def scenario = Scenario.Java.of(
                "valid-converter",
                "scenarios/java/Quantity.java",
                "scenarios/converters/ValidConverter.java")
                .withDependencies(dependencySpringDataJdbc, dependencySpringDataRelational, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.jdbc.converters", "scenarios.converters.ValidConverter")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and: 'generated file contains the user converter and the marker comment'
        def generated = testkitJava.generatedSourcePath(projectDir, "test/boundary/persistence/$GENERATED_FILE_NAME").toFile().text
        generated.contains("new scenarios.converters.ValidConverter()")
        generated.contains("// user-supplied via lazyval.springdata.jdbc.converters:")
    }

    void "multiple valid user converters are all appended in declared order"() {
        given:
        def scenario = Scenario.Java.of(
                "multiple-valid-converters",
                "scenarios/java/Quantity.java",
                "scenarios/converters/ValidConverter.java",
                "scenarios/converters/AnotherValidConverter.java")
                .withDependencies(dependencySpringDataJdbc, dependencySpringDataRelational, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.jdbc.converters",
                "scenarios.converters.ValidConverter,scenarios.converters.AnotherValidConverter")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and:
        def generated = testkitJava.generatedSourcePath(projectDir, "test/boundary/persistence/$GENERATED_FILE_NAME").toFile().text
        def validIdx = generated.indexOf("new scenarios.converters.ValidConverter()")
        def anotherIdx = generated.indexOf("new scenarios.converters.AnotherValidConverter()")
        validIdx >= 0
        anotherIdx > validIdx
    }

    void "whitespace and empty segments in option are tolerated"() {
        given:
        def scenario = Scenario.Java.of(
                "whitespace-in-option",
                "scenarios/java/Quantity.java",
                "scenarios/converters/ValidConverter.java",
                "scenarios/converters/AnotherValidConverter.java")
                .withDependencies(dependencySpringDataJdbc, dependencySpringDataRelational, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.jdbc.converters",
                " scenarios.converters.ValidConverter , , scenarios.converters.AnotherValidConverter ")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and:
        def generated = testkitJava.generatedSourcePath(projectDir, "test/boundary/persistence/$GENERATED_FILE_NAME").toFile().text
        generated.contains("new scenarios.converters.ValidConverter()")
        generated.contains("new scenarios.converters.AnotherValidConverter()")
    }

    void "missing class FQN fails the build"() {
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencySpringDataJdbc, dependencySpringDataRelational, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.jdbc.converters", "com.example.Missing")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("lazyval.springdata.jdbc.converters") && it.contains("com.example.Missing") && it.contains("not found on compile classpath")
        }
    }

    void "class that does not implement Converter fails the build"() {
        given:
        def scenario = Scenario.Java.of(
                "not-a-converter",
                "scenarios/java/Quantity.java",
                "scenarios/converters/NotAConverter.java")
                .withDependencies(dependencySpringDataJdbc, dependencySpringDataRelational, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.jdbc.converters", "scenarios.converters.NotAConverter")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("scenarios.converters.NotAConverter") && it.contains("does not implement") && it.contains("Converter")
        }
    }

    void "converter without @ReadingConverter or @WritingConverter fails the build"() {
        given:
        def scenario = Scenario.Java.of(
                "unannotated-converter",
                "scenarios/java/Quantity.java",
                "scenarios/converters/UnannotatedConverter.java")
                .withDependencies(dependencySpringDataJdbc, dependencySpringDataRelational, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.jdbc.converters", "scenarios.converters.UnannotatedConverter")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("scenarios.converters.UnannotatedConverter") && it.contains("@ReadingConverter")
        }
    }

    void "converter without no-arg constructor fails the build"() {
        given:
        def scenario = Scenario.Java.of(
                "no-noarg-converter",
                "scenarios/java/Quantity.java",
                "scenarios/converters/NoNoArgConverter.java")
                .withDependencies(dependencySpringDataJdbc, dependencySpringDataRelational, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.jdbc.converters", "scenarios.converters.NoNoArgConverter")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("scenarios.converters.NoNoArgConverter") && it.contains("no-arg constructor")
        }
    }

    void "option set but jdbc Spring Data missing produces warning, not failure"() {
        given: 'only Cassandra is on the classpath but the JDBC option is set'
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.jdbc.converters", "com.example.Whatever")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.SuccessWithWarnings
        def success = result as Testresult.Java.SuccessWithWarnings
        success.generatedFiles().contains(GENERATED_FILE_NAME)

        and:
        success.warnings().any {
            it.contains("lazyval.springdata.jdbc.converters") && it.contains("JDBC") && it.contains("ignored")
        }

        and: 'no jdbcCustomConversions method is generated'
        def generated = testkitJava.generatedSourcePath(projectDir, "test/boundary/persistence/$GENERATED_FILE_NAME").toFile().text
        !generated.contains("jdbcCustomConversions")
    }
}
