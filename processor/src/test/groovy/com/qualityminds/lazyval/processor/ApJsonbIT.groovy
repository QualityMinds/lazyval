package com.qualityminds.lazyval.processor

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import org.eclipse.collections.api.factory.Lists
import spock.lang.*

import java.nio.file.Path

@Title("Generator Integration - JSON-B")
class ApJsonbIT extends Specification {

    public static final Dependency dependencyJsonbApi = new Dependency("jakarta.json.bind", "jakarta.json.bind-api", "3.0.1")
    public static final Dependency dependencyJaxRsApi = new Dependency("jakarta.ws.rs", "jakarta.ws.rs-api", "3.1.0")
    public static final Dependency dependencyJakartaInject = new Dependency("jakarta.inject", "jakarta.inject-api", "2.0.1")
    public static final Dependency dependencyQuarkusJsonb = new Dependency("io.quarkus", "quarkus-jsonb", "3.34.5")

    private static final String GENERATED_FILE_NAME = "LazyvalJsonbAdapters.java"
    private static final String GENERATED_RESOLVER_FILE_NAME = "LazyvalJsonbContextResolver.java"

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    @Unroll("#scenario.name() compiles and generated #expected.generatedFiles()")
    void "JSON-B with all Scenarios"(){
        given:
        scenario.withDependencies(dependencyJsonbApi)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario << Scenario.Java.all()
        expected = new Testresult.Java.Success(GENERATED_FILE_NAME)
    }

    void "JSON-B generated at correct default location when no override is given"(){
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencyJsonbApi)

        when:
        testkitJava.run(projectDir, scenario)

        then: 'file is generated at correct location using base-package with generator-default'
        projectDir.resolve("build/generated/test/$GENERATED_FILE_NAME").toFile().exists()
    }

    void "JSON-B package override by generator works as expected"(){
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencyJsonbApi)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.jsonb.package", "test.custom")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Java.Success(Lists.immutable.of(GENERATED_FILE_NAME))

        and: 'file is at correct package'
        projectDir.resolve("build/generated/test/custom/$GENERATED_FILE_NAME").toFile().exists()
    }

    @Unroll("#scenario.name() compiles and generated ContextResolver with JAX-RS on classpath")
    void "JSON-B with JAX-RS generates ContextResolver"(){
        given:
        scenario.withDependencies(dependencyJsonbApi, dependencyJaxRsApi)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario << Scenario.Java.all()
        expected = new Testresult.Java.Success(GENERATED_FILE_NAME, GENERATED_RESOLVER_FILE_NAME)
    }

    void "JSON-B ContextResolver is generated at correct default location"(){
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencyJsonbApi, dependencyJaxRsApi)

        when:
        testkitJava.run(projectDir, scenario)

        then: 'adapters file is generated'
        projectDir.resolve("build/generated/test/$GENERATED_FILE_NAME").toFile().exists()

        and: 'ContextResolver file is generated'
        projectDir.resolve("build/generated/test/$GENERATED_RESOLVER_FILE_NAME").toFile().exists()
    }

    void "JSON-B ContextResolver is not generated when register is false"(){
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencyJsonbApi, dependencyJaxRsApi)
        and: 'registration is disabled'
        scenario.withOption("lazyval.jsonb.register", "false")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'only adapters file is generated'
        result == new Testresult.Java.Success(Lists.immutable.of(GENERATED_FILE_NAME))

        and: 'ContextResolver file is not generated'
        !projectDir.resolve("build/generated/test/$GENERATED_RESOLVER_FILE_NAME").toFile().exists()
    }

    void "JSON-B emits a Singleton JsonbConfigCustomizer when Quarkus is on the classpath"(){
        given: 'Quarkus quarkus-jsonb (provides io.quarkus.jsonb.JsonbConfigCustomizer) is on the classpath'
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencyJsonbApi, dependencyQuarkusJsonb, dependencyJakartaInject)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'only the adapters file is generated — the LazyvalJsonbAdapters itself becomes the customizer'
        result == new Testresult.Java.Success(Lists.immutable.of(GENERATED_FILE_NAME))

        and: 'no ContextResolver is emitted (avoids registering the same adapters twice in Quarkus REST)'
        !projectDir.resolve("build/generated/test/$GENERATED_RESOLVER_FILE_NAME").toFile().exists()
    }

    void "JSON-B ContextResolver is suppressed when both JAX-RS and Quarkus are on the classpath"(){
        given: 'JAX-RS and Quarkus quarkus-jsonb are both on the classpath'
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencyJsonbApi, dependencyJaxRsApi,
                        dependencyQuarkusJsonb, dependencyJakartaInject)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'Quarkus wins: only adapters file is generated, no resolver'
        result == new Testresult.Java.Success(Lists.immutable.of(GENERATED_FILE_NAME))

        and: 'ContextResolver file is not generated'
        !projectDir.resolve("build/generated/test/$GENERATED_RESOLVER_FILE_NAME").toFile().exists()
    }
}
