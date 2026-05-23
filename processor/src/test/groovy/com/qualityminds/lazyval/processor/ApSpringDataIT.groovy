package com.qualityminds.lazyval.processor

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.*

import java.nio.file.Path

@Title("Generator Integration - Spring Data")
class ApSpringDataIT extends Specification {

    public static final Dependency dependencySpringDataCassandra = new Dependency("org.springframework.data", "spring-data-cassandra", "4.4.6")
    public static final Dependency dependencySpringDataCommons = new Dependency("org.springframework.data", "spring-data-commons", "3.4.6")
    public static final Dependency dependencySpringCore = new Dependency("org.springframework", "spring-core", "6.2.7")
    public static final Dependency dependencySpringBeans = new Dependency("org.springframework", "spring-beans", "6.2.7")
    public static final Dependency dependencySpringContext = new Dependency("org.springframework", "spring-context", "6.2.7")

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    @Unroll("#scenario.name() compiles and generates LazyvalSpringDataConfiguration.java")
    void "Spring Data with all Scenarios"(){
        given:
        scenario.withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success("LazyvalSpringDataConfiguration.java")

        where:
        scenario << Scenario.Java.all()
    }

    void "Converters generated at correct default location when no override is given"(){
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)

        when:
        testkitJava.run(projectDir, scenario)

        then: 'single configuration file is generated at correct location using base-package with generator-default'
        projectDir.resolve("build/generated/test/boundary/persistence/cassandra/LazyvalSpringDataConfiguration.java").toFile().exists()
    }

    void "Package override by generator works as expected"(){
        given:
        def scenario = Scenario.Java.quantity().withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.spring_data.package", "test.custom")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Java.Success("LazyvalSpringDataConfiguration.java")

        and: 'file is at correct package'
        projectDir.resolve("build/generated/test/custom/LazyvalSpringDataConfiguration.java").toFile().exists()
    }

    void "Birthday wrapping LocalDate generates valid converters"(){
        given:
        def scenario = Scenario.Java.birthday()
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success("LazyvalSpringDataConfiguration.java")
    }

    void "Nothing generated when no CustomConversions is on classpath"(){
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'no file is generated without a store-specific CustomConversions on the classpath'
        result == new Testresult.Java.NothingGenerated()
    }
}
