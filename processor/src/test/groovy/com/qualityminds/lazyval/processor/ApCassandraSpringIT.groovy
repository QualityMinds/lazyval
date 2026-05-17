package com.qualityminds.lazyval.processor

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import org.eclipse.collections.api.factory.Lists
import spock.lang.*

import java.nio.file.Path

@Title("Generator Integration - Cassandra Spring Data")
class ApCassandraSpringIT extends Specification {

    public static final Dependency dependencySpringDataCassandra = new Dependency("org.springframework.data", "spring-data-cassandra", "4.4.6")
    public static final Dependency dependencySpringDataCommons = new Dependency("org.springframework.data", "spring-data-commons", "3.4.6")
    public static final Dependency dependencySpringCore = new Dependency("org.springframework", "spring-core", "6.2.7")
    public static final Dependency dependencySpringBeans = new Dependency("org.springframework", "spring-beans", "6.2.7")
    public static final Dependency dependencySpringContext = new Dependency("org.springframework", "spring-context", "6.2.7")

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    @Unroll("#scenario.name() compiles and generated #expected.generatedFiles()")
    void "Cassandra Spring Data with all Scenarios"(){
        given:
        scenario.withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario << Scenario.Java.all()
        readConverter = scenario.name().replace(".java", "ReadConverter.java")
        writeConverter = scenario.name().replace(".java", "WriteConverter.java")
        expected = new Testresult.Java.Success(readConverter, writeConverter, "LazyvalCassandraSpringDataConfiguration.java")
    }

    void "Converters generated at correct default location when no override is given"(){
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)

        when:
        testkitJava.run(projectDir, scenario)

        then: 'file is generated at correct location using base-package with generator-default'
        projectDir.resolve("build/generated/test/boundary/persistence/cassandra/QuantityReadConverter.java").toFile().exists()
        projectDir.resolve("build/generated/test/boundary/persistence/cassandra/QuantityWriteConverter.java").toFile().exists()
        projectDir.resolve("build/generated/test/boundary/persistence/cassandra/LazyvalCassandraSpringDataConfiguration.java").toFile().exists()
    }

    void "Package override by generator works as expected"(){
        given:
        def scenario = Scenario.Java.quantity().withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.cassandra_spring_data.package", "test.custom")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Java.Success(Lists.immutable.of("QuantityReadConverter.java", "QuantityWriteConverter.java", "LazyvalCassandraSpringDataConfiguration.java"))

        and: 'file is at correct package'
        projectDir.resolve("build/generated/test/custom/QuantityReadConverter.java").toFile().exists()
        projectDir.resolve("build/generated/test/custom/QuantityWriteConverter.java").toFile().exists()
        projectDir.resolve("build/generated/test/custom/LazyvalCassandraSpringDataConfiguration.java").toFile().exists()
    }

    void "Birthday wrapping LocalDate generates valid converters"(){
        given:
        def scenario = Scenario.Java.birthday()
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success("BirthdayReadConverter.java", "BirthdayWriteConverter.java", "LazyvalCassandraSpringDataConfiguration.java")
    }
}
