package com.qualityminds.lazyval.processor

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.*

import java.nio.file.Files
import java.nio.file.Path

@Title("Generator Integration - Spring Data Cassandra")
class ApSpringDataCassandraIT extends Specification {

    public static final Dependency dependencySpringDataCassandra = new Dependency("org.springframework.data", "spring-data-cassandra", "4.4.6")
    public static final Dependency dependencySpringDataMongo = new Dependency("org.springframework.data", "spring-data-mongodb", "4.4.6")
    public static final Dependency dependencySpringDataCommons = new Dependency("org.springframework.data", "spring-data-commons", "3.4.6")
    public static final Dependency dependencySpringCore = new Dependency("org.springframework", "spring-core", "6.2.7")
    public static final Dependency dependencySpringBeans = new Dependency("org.springframework", "spring-beans", "6.2.7")
    public static final Dependency dependencySpringContext = new Dependency("org.springframework", "spring-context", "6.2.7")

    private static final String GENERATED_FILE_NAME = "LazyvalSpringDataConfiguration.java"

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    @Unroll("#scenario.name() compiles and generates LazyvalSpringDataConfiguration.java for Cassandra")
    void "all Scenarios compile and generate the configuration"() {
        given:
        scenario.withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and: 'contains @Generated'
        Files.readString(projectDir.resolve("build/generated/test/boundary/persistence/$GENERATED_FILE_NAME")).contains("@Generated")

        where:
        scenario << Scenario.Java.all()
    }

    void "Spring Data without database generates Nothing"() {
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.NothingGenerated()
    }

    void "Nothing generated when no CustomConversions is on classpath"() {
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.NothingGenerated()
    }

    void "Converters generated at correct default location when no override is given"() {
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)

        when:
        testkitJava.run(projectDir, scenario)

        then: 'single configuration file is generated at correct location using base-package with generator-default'
        projectDir.resolve("build/generated/test/boundary/persistence/LazyvalSpringDataConfiguration.java").toFile().exists()
    }

    void "Package override by generator works as expected"() {
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.springdata.package", "test.custom")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and: 'file is at correct package'
        projectDir.resolve("build/generated/test/custom/LazyvalSpringDataConfiguration.java").toFile().exists()
    }

    void "OrderDate wrapping LocalDate generates valid converters"() {
        given:
        def scenario = Scenario.Java.orderDate()
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)
    }

    void "single valid user converter is appended to cassandraCustomConversions"() {
        given:
        def scenario = Scenario.Java.of("scenarios/java/Quantity.java", "scenarios/converters/ValidConverter.java")
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters", "scenarios.converters.ValidConverter")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and: 'generated file contains the user converter and the marker comment'
        def generated = projectDir.resolve("build/generated/test/boundary/persistence/LazyvalSpringDataConfiguration.java").toFile().text
        generated.contains("new scenarios.converters.ValidConverter()")
        generated.contains("// user-supplied via lazyval.springdata.cassandra.converters:")
    }

    void "multiple valid user converters are all appended in declared order"() {
        given:
        def scenario = Scenario.Java.of("scenarios/java/Quantity.java",
                "scenarios/converters/ValidConverter.java",
                "scenarios/converters/AnotherValidConverter.java")
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters",
                "scenarios.converters.ValidConverter,scenarios.converters.AnotherValidConverter")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and:
        def generated = projectDir.resolve("build/generated/test/boundary/persistence/LazyvalSpringDataConfiguration.java").toFile().text
        def validIdx = generated.indexOf("new scenarios.converters.ValidConverter()")
        def anotherIdx = generated.indexOf("new scenarios.converters.AnotherValidConverter()")
        validIdx >= 0
        anotherIdx > validIdx
    }

    void "whitespace and empty segments in option are tolerated"() {
        given:
        def scenario = Scenario.Java.of("scenarios/java/Quantity.java",
                "scenarios/converters/ValidConverter.java",
                "scenarios/converters/AnotherValidConverter.java")
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters",
                " scenarios.converters.ValidConverter , , scenarios.converters.AnotherValidConverter ")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and:
        def generated = projectDir.resolve("build/generated/test/boundary/persistence/LazyvalSpringDataConfiguration.java").toFile().text
        generated.contains("new scenarios.converters.ValidConverter()")
        generated.contains("new scenarios.converters.AnotherValidConverter()")
    }

    void "missing class FQN fails the build"() {
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters", "com.example.Missing")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        def failure = result as Testresult.Java.Failure
        failure.errors().any {
            it.contains("lazyval.springdata.cassandra.converters") && it.contains("com.example.Missing") && it.contains("not found on compile classpath")
        }
    }

    void "class that does not implement Converter fails the build"() {
        given:
        def scenario = Scenario.Java.of("scenarios/java/Quantity.java", "scenarios/converters/NotAConverter.java")
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters", "scenarios.converters.NotAConverter")

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
        given: 'config is generated at default location, converter lives in scenarios.converters'
        def scenario = Scenario.Java.of("scenarios/java/Quantity.java", "scenarios/converters/NonPublicConverter.java")
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters", "scenarios.converters.NonPublicConverter")

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
        def scenario = Scenario.Java.of("scenarios/java/Quantity.java", "scenarios/converters/NonPublicConverter.java")
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.package", "scenarios.converters")
        scenario.withOption("lazyval.springdata.cassandra.converters", "scenarios.converters.NonPublicConverter")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and:
        def generated = projectDir.resolve("build/generated/scenarios/converters/LazyvalSpringDataConfiguration.java").toFile().text
        generated.contains("new scenarios.converters.NonPublicConverter()")
    }

    void "unconditionally inaccessible converter class fails the build"() {
        given: 'NonAccessibleConverter.Inner is a private static nested class — unreachable from anywhere'
        def scenario = Scenario.Java.of("scenarios/java/Quantity.java", "scenarios/converters/NonAccessibleConverter.java")
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters", "scenarios.converters.NonAccessibleConverter.Inner")

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
        def scenario = Scenario.Java.of("scenarios/java/Quantity.java", "scenarios/converters/UnannotatedConverter.java")
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters", "scenarios.converters.UnannotatedConverter")

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
        def scenario = Scenario.Java.of("scenarios/java/Quantity.java", "scenarios/converters/NoNoArgConverter.java")
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters", "scenarios.converters.NoNoArgConverter")

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
        def scenario = Scenario.Java.of("scenarios/java/Quantity.java",
                "scenarios/converters/NotAConverter.java",
                "scenarios/converters/NoNoArgConverter.java")
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters",
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

    void "option set but spring-data-cassandra missing produces warning, not failure"() {
        given: 'only MongoDB is on the classpath but Cassandra option is set'
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencySpringDataMongo, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        scenario.withOption("lazyval.springdata.cassandra.converters", "com.example.Whatever")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.SuccessWithWarnings
        def success = result as Testresult.Java.SuccessWithWarnings
        success.generatedFiles().contains(GENERATED_FILE_NAME)

        and:
        success.warnings().any {
            it.contains("lazyval.springdata.cassandra.converters") && it.contains("Cassandra") && it.contains("ignored")
        }

        and: 'no cassandra bean method is generated'
        def generated = projectDir.resolve("build/generated/test/boundary/persistence/LazyvalSpringDataConfiguration.java").toFile().text
        !generated.contains("cassandraCustomConversions")
    }
}
