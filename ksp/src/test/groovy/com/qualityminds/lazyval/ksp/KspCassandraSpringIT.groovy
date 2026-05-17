package com.qualityminds.lazyval.ksp

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import org.eclipse.collections.api.factory.Lists
import spock.lang.*

import java.nio.file.Path

@Title("Generator Integration - Cassandra Spring Data")
class KspCassandraSpringIT extends Specification {

    public static final Dependency dependencySpringDataCassandra = new Dependency("org.springframework.data", "spring-data-cassandra", "4.4.6")
    public static final Dependency dependencySpringDataCommons = new Dependency("org.springframework.data", "spring-data-commons", "3.4.6")
    public static final Dependency dependencySpringCore = new Dependency("org.springframework", "spring-core", "6.2.7")
    public static final Dependency dependencySpringBeans = new Dependency("org.springframework", "spring-beans", "6.2.7")
    public static final Dependency dependencySpringContext = new Dependency("org.springframework", "spring-context", "6.2.7")

    @TempDir()
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    @Unroll("#scenario.name() compiles and generated #expected.generatedFiles()")
    void "Cassandra Spring Data with all Scenarios"(){
        given:
        scenario.withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario << Scenario.Kotlin.all()
        readConverter = scenario.name().replace(".kt", "ReadConverter.kt")
        writeConverter = scenario.name().replace(".kt", "WriteConverter.kt")
        expected = new Testresult.Kotlin.Success(readConverter, writeConverter, "LazyvalCassandraSpringDataConfiguration.kt")
    }

    void "Converters generated at correct default location when no override is given"(){
        given:
        def scenario = Scenario.Kotlin.quantity()
                .withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)

        when:
        testkitKotlin.run(projectDir, scenario)

        then: 'file is generated at correct location using base-package with generator-default'
        projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/cassandra/QuantityReadConverter.kt").toFile().exists()
        projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/cassandra/QuantityWriteConverter.kt").toFile().exists()
        projectDir.resolve("build/generated/ksp/kotlin/test/boundary/persistence/cassandra/LazyvalCassandraSpringDataConfiguration.kt").toFile().exists()
    }

    void "Package override by generator works as expected"(){
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(dependencySpringDataCassandra, dependencySpringDataCommons, dependencySpringCore, dependencySpringBeans, dependencySpringContext)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.cassandra_spring_data.package", "test.custom")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Kotlin.Success(Lists.immutable.of("QuantityReadConverter.kt", "QuantityWriteConverter.kt", "LazyvalCassandraSpringDataConfiguration.kt"))

        and: 'file is at correct package'
        projectDir.resolve("build/generated/ksp/kotlin/test/custom/QuantityReadConverter.kt").toFile().exists()
        projectDir.resolve("build/generated/ksp/kotlin/test/custom/QuantityWriteConverter.kt").toFile().exists()
        projectDir.resolve("build/generated/ksp/kotlin/test/custom/LazyvalCassandraSpringDataConfiguration.kt").toFile().exists()
    }
}
