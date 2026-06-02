package com.qualityminds.lazyval.processor

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import org.eclipse.collections.api.factory.Lists
import spock.lang.*

import java.nio.file.Path

@Title("Generator Integration - Cassandra Codec")
class ApCassandraCodecIT extends Specification {

    public static final Dependency dependencyDriverCore = new Dependency("com.datastax.oss", "java-driver-core", "4.17.0")
    public static final Dependency dependencyCassandraQuarkusExtension = new Dependency("com.datastax.oss.quarkus", "cassandra-quarkus-client", "1.4.1")
    public static final Dependency dependencyCdi = new Dependency("jakarta.enterprise", "jakarta.enterprise.cdi-api", "4.1.0")
    public static final Dependency dependencyInject = new Dependency("jakarta.inject", "jakarta.inject-api", "2.0.1")
    public static final Dependency dependencyJakartaAnnotation = new Dependency("jakarta.annotation", "jakarta.annotation-api", "3.0.0")
    public static final Dependency dependencyQuarkus = new Dependency("io.quarkus", "quarkus-core", "3.34.5")
    public static final Dependency dependencyQuarkusArc = new Dependency("io.quarkus.arc", "arc", "3.34.5")
    public static final Dependency dependencyQuarkusNetty = new Dependency("io.quarkus", "quarkus-netty", "3.34.5")
    public static final Dependency dependencyNetty = new Dependency("io.netty", "netty-transport", "4.1.132.Final")

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    @Unroll("#scenario.name() compiles and generated #expected.generatedFiles()")
    void "Cassandra Codec with all Scenarios"(){
        given:
        scenario.withDependencies(dependencyDriverCore)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario << Scenario.Java.all()
        expected = new Testresult.Java.Success("LazyvalCassandraCodecs.java")
    }

    void "Codec generated at correct default location when no override is given"(){
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencyDriverCore)

        when:
        testkitJava.run(projectDir, scenario)

        then: 'file is generated at correct location using base-package with generator-default'
        projectDir.resolve("build/generated/test/boundary/persistence/cassandra/LazyvalCassandraCodecs.java").toFile().exists()
    }

    void "Package override by generator works as expected"(){
        given:
        def scenario = Scenario.Java.quantity().withDependencies(dependencyDriverCore)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.cassandra.package", "test.custom")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Java.Success("LazyvalCassandraCodecs.java")

        and: 'file is at correct package'
        projectDir.resolve("build/generated/test/custom/LazyvalCassandraCodecs.java").toFile().exists()
    }

    void "OrderDate wrapping LocalDate generates a valid codec"(){
        given:
        def scenario = Scenario.Java.orderDate()
                .withDependencies(dependencyDriverCore)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success("LazyvalCassandraCodecs.java")
    }

    void "Quarkus Registration generated when Cassandra-Quarkus-Extension is available"(){
        given:
        def scenario = Scenario.Java.orderDate()
                .withDependencies(
                        dependencyDriverCore, dependencyCassandraQuarkusExtension,
                        // compile dependencies
                        dependencyCdi,
                        dependencyInject,
                        dependencyJakartaAnnotation,
                        dependencyQuarkus,
                        dependencyQuarkusArc,
                        dependencyQuarkusNetty,
                        dependencyNetty)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success("LazyvalCassandraCodecs.java", "LazyvalCassandraCodecRegistrar.java")
    }
}
