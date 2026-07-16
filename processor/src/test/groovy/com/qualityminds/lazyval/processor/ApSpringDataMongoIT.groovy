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
class ApSpringDataMongoIT extends Specification {

    public static final Dependency dependencySpringDataMongo = new Dependency("org.springframework.data", "spring-data-mongodb", "4.4.6")
    public static final Dependency dependencySpringDataCassandra = new Dependency("org.springframework.data", "spring-data-cassandra", "4.4.6")
    public static final Dependency dependencySpringDataCommons = new Dependency("org.springframework.data", "spring-data-commons", "3.4.6")
    public static final Dependency dependencySpringCore = new Dependency("org.springframework", "spring-core", "6.2.7")
    public static final Dependency dependencySpringBeans = new Dependency("org.springframework", "spring-beans", "6.2.7")
    public static final Dependency dependencySpringContext = new Dependency("org.springframework", "spring-context", "6.2.7")
    public static final Dependency dependencyBson = new Dependency("org.mongodb", "bson", "5.6.5")

    private static final String GENERATED_FILE_NAME = "LazyvalSpringDataConfiguration.java"

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    void "Spring Data MongoDB with combined Scenarios"() {
        given: 'a compiler run with all sources'
        def scenario = Scenario.Java.combined()
                .withDependencies(
                        dependencySpringDataMongo,
                        dependencySpringDataCommons,
                        dependencySpringCore,
                        dependencySpringBeans,
                        dependencySpringContext,
                        // test supersede
                        dependencyBson)

        and: 'a defined approval for the generated configuration'
        List<Approval.ForJava> approvals = [
                Approval.JavaSource.at("test/boundary/persistence/$GENERATED_FILE_NAME",
                        "approvals/springdata/mongo/$GENERATED_FILE_NAME")
        ]

        when:
        def result = testkitJava.run(projectDir, scenario, approvals)

        then:
        result == Testresult.Java.Approved.of(approvals)
    }

    void "Spring Data MongoDB with combined Scenarios and disabled supersede"() {
        given: 'a compiler run with all sources'
        def scenario = Scenario.Java.combined()
                .withDependencies(
                        dependencySpringDataMongo,
                        dependencySpringDataCommons,
                        dependencySpringCore,
                        dependencySpringBeans,
                        dependencySpringContext,
                        // test supersede
                        dependencyBson
                )
                .withOption("lazyval.generators.supersede", "false")

        and: 'a defined approval for the generated configuration'
        List<Approval.ForJava> approvals = [
                Approval.JavaSource.at("test/boundary/persistence/$GENERATED_FILE_NAME",
                        "approvals/springdata/mongo/$GENERATED_FILE_NAME")
        ]

        when:
        def result = testkitJava.run(projectDir, scenario, approvals)

        then: 'Mongo Codec was generated as well'
        result == Testresult.Java.ApprovalMismatch.of([
                new Testresult.Java.ApprovalMismatch.Failure.UnexpectedFile("test/boundary/persistence/mongodb/LazyvalMongoCodecs.java")
        ])
    }

    void "single valid user converter is appended to mongoCustomConversions"() {
        given:
        def scenario = Scenario.Java.of(
                "valid-converter",
                "scenarios/java/Quantity.java",
                "scenarios/converters/ValidConverter.java")
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.mongo.converters", "scenarios.converters.ValidConverter")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and: 'generated file contains the user converter and the marker comment'
        def generated = testkitJava.generatedSourcePath(projectDir, "test/boundary/persistence/LazyvalSpringDataConfiguration.java").toFile().text
        generated.contains("new scenarios.converters.ValidConverter()")
        generated.contains("// user-supplied via lazyval.springdata.mongo.converters:")
    }

    void "multiple valid user converters are all appended in declared order"() {
        given:
        def scenario = Scenario.Java.of(
                "multiple-valid-converters",
                "scenarios/java/Quantity.java",
                "scenarios/converters/ValidConverter.java",
                "scenarios/converters/AnotherValidConverter.java")
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.mongo.converters",
                "scenarios.converters.ValidConverter,scenarios.converters.AnotherValidConverter")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and:
        def generated = testkitJava.generatedSourcePath(projectDir, "test/boundary/persistence/LazyvalSpringDataConfiguration.java").toFile().text
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
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.mongo.converters",
                " scenarios.converters.ValidConverter , , scenarios.converters.AnotherValidConverter ")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and:
        def generated = testkitJava.generatedSourcePath(projectDir, "test/boundary/persistence/LazyvalSpringDataConfiguration.java").toFile().text
        generated.contains("new scenarios.converters.ValidConverter()")
        generated.contains("new scenarios.converters.AnotherValidConverter()")
    }

    void "missing class FQN fails the build"() {
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.mongo.converters", "com.example.Missing")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("lazyval.springdata.mongo.converters") && it.contains("com.example.Missing") && it.contains("not found on compile classpath")
        }
    }

    void "class that does not implement Converter fails the build"() {
        given:
        def scenario = Scenario.Java.of(
                "not-a-converter",
                "scenarios/java/Quantity.java",
                "scenarios/converters/NotAConverter.java")
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.mongo.converters", "scenarios.converters.NotAConverter")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("scenarios.converters.NotAConverter") && it.contains("does not implement") && it.contains("Converter")
        }
    }

    void "package-private converter in different package fails the build"() {
        given: 'config is generated at default location (test.boundary.persistence), converter lives in scenarios.converters'
        def scenario = Scenario.Java.of(
                "not-accessible",
                "scenarios/java/Quantity.java",
                "scenarios/converters/NonPublicConverter.java")
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.mongo.converters", "scenarios.converters.NonPublicConverter")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("scenarios.converters.NonPublicConverter") && it.contains("not accessible")
        }
    }

    void "package-private converter in same package as generated config succeeds"() {
        given: 'configuration class is generated into the converter package'
        def scenario = Scenario.Java.of(
                "package-private-converter",
                "scenarios/java/Quantity.java",
                "scenarios/converters/NonPublicConverter.java")
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.package", "scenarios.converters")
        scenario.withOption("lazyval.springdata.mongo.converters", "scenarios.converters.NonPublicConverter")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and:
        def generated = testkitJava.generatedSourcePath(projectDir, "scenarios/converters/LazyvalSpringDataConfiguration.java").toFile().text
        generated.contains("new scenarios.converters.NonPublicConverter()")
    }

    void "unconditionally inaccessible converter class fails the build"() {
        given: 'NonAccessibleConverter.Inner is a private static nested class — unreachable from anywhere'
        def scenario = Scenario.Java.of(
                "inner-private-not-accessible",
                "scenarios/java/Quantity.java",
                "scenarios/converters/NonAccessibleConverter.java")
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.mongo.converters", "scenarios.converters.NonAccessibleConverter.Inner")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("scenarios.converters.NonAccessibleConverter.Inner") && it.contains("not accessible")
        }
    }

    void "converter without @ReadingConverter or @WritingConverter fails the build"() {
        given:
        def scenario = Scenario.Java.of(
                "missing-annotations",
                "scenarios/java/Quantity.java",
                "scenarios/converters/UnannotatedConverter.java")
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.mongo.converters", "scenarios.converters.UnannotatedConverter")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("scenarios.converters.UnannotatedConverter") && it.contains("@ReadingConverter") && it.contains("@WritingConverter")
        }
    }

    void "converter without no-arg constructor fails the build"() {
        given:
        def scenario = Scenario.Java.of(
                "missing-no-arg-ctor",
                "scenarios/java/Quantity.java",
                "scenarios/converters/NoNoArgConverter.java")
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.mongo.converters", "scenarios.converters.NoNoArgConverter")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("scenarios.converters.NoNoArgConverter") && it.contains("no-arg constructor")
        }
    }

    void "two invalid FQNs report both errors in one build"() {
        given:
        def scenario = Scenario.Java.of(
                "invalid-fqns-reported",
                "scenarios/java/Quantity.java",
                "scenarios/converters/NotAConverter.java",
                "scenarios/converters/NoNoArgConverter.java")
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.mongo.converters",
                "scenarios.converters.NotAConverter,scenarios.converters.NoNoArgConverter")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure

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
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.mongo.converters", "com.example.Whatever")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.SuccessWithWarnings
        def success = result as Testresult.Java.SuccessWithWarnings
        success.generatedFiles().contains(GENERATED_FILE_NAME)

        and:
        success.warnings().any {
            it.contains("lazyval.springdata.mongo.converters") && it.contains("MongoDB") && it.contains("ignored")
        }

        and: 'no mongo bean method is generated'
        def generated = testkitJava.generatedSourcePath(projectDir, "test/boundary/persistence/LazyvalSpringDataConfiguration.java").toFile().text
        !generated.contains("mongoCustomConversions")
    }
}
