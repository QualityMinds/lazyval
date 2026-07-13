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

    void "JSON-B with combined Scenarios"() {
        given: 'a compiler run with all sources'
        def scenario = Scenario.Java.combined().withDependencies(dependencyJsonbApi)

        and: 'a defined approval for the generated adapters file'
        List<Approval.ForJava> approvals = [
                Approval.JavaSource.at("test/$GENERATED_FILE_NAME", "approvals/jsonb/$GENERATED_FILE_NAME")
        ]

        when:
        def result = testkitJava.run(projectDir, scenario, approvals)

        then:
        result == Testresult.Java.Approved.of(approvals)
    }

    void "JSON-B generated at correct default location when no override is given"(){
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencyJsonbApi)

        when:
        testkitJava.run(projectDir, scenario)

        then: 'file is generated at correct location using base-package with generator-default'
        testkitJava.generatedSourcePath(projectDir, "test/$GENERATED_FILE_NAME").toFile().exists()
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
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and: 'file is at correct package'
        testkitJava.generatedSourcePath(projectDir, "test/custom/$GENERATED_FILE_NAME").toFile().exists()
    }

    void "JSON-B with JAX-RS generates ContextResolver for combined Scenarios"() {
        given:
        def scenario = Scenario.Java.combined().withDependencies(dependencyJsonbApi, dependencyJaxRsApi)

        and: 'a defined approval for adapters and context resolver'
        List<Approval.ForJava> approvals = [
                Approval.JavaSource.at("test/$GENERATED_FILE_NAME", "approvals/jsonb/$GENERATED_FILE_NAME"),
                Approval.JavaSource.at("test/$GENERATED_RESOLVER_FILE_NAME", "approvals/jsonb/$GENERATED_RESOLVER_FILE_NAME")
        ]

        when:
        def result = testkitJava.run(projectDir, scenario, approvals)

        then:
        result == Testresult.Java.Approved.of(approvals)
    }

    void "JSON-B ContextResolver is generated at correct default location"(){
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencyJsonbApi, dependencyJaxRsApi)

        when:
        testkitJava.run(projectDir, scenario)

        then: 'adapters file is generated'
        testkitJava.generatedSourcePath(projectDir, "test/$GENERATED_FILE_NAME").toFile().exists()

        and: 'ContextResolver file is generated'
        testkitJava.generatedSourcePath(projectDir, "test/$GENERATED_RESOLVER_FILE_NAME").toFile().exists()
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
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and: 'ContextResolver file is not generated'
        !testkitJava.generatedSourcePath(projectDir, "test/$GENERATED_RESOLVER_FILE_NAME").toFile().exists()
    }

    void "JSON-B emits a Singleton JsonbConfigCustomizer when Quarkus is on the classpath"(){
        given: 'Quarkus quarkus-jsonb (provides io.quarkus.jsonb.JsonbConfigCustomizer) is on the classpath'
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencyJsonbApi, dependencyQuarkusJsonb, dependencyJakartaInject)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'only the adapters file is generated — the LazyvalJsonbAdapters itself becomes the customizer'
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and: 'no ContextResolver is emitted (avoids registering the same adapters twice in Quarkus REST)'
        !testkitJava.generatedSourcePath(projectDir, "test/$GENERATED_RESOLVER_FILE_NAME").toFile().exists()
    }

    void "JSON-B ContextResolver is suppressed when both JAX-RS and Quarkus are on the classpath"(){
        given: 'JAX-RS and Quarkus quarkus-jsonb are both on the classpath'
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencyJsonbApi, dependencyJaxRsApi,
                        dependencyQuarkusJsonb, dependencyJakartaInject)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'Quarkus wins: only adapters file is generated, no resolver'
        result == new Testresult.Java.Success(GENERATED_FILE_NAME)

        and: 'ContextResolver file is not generated'
        !testkitJava.generatedSourcePath(projectDir, "test/$GENERATED_RESOLVER_FILE_NAME").toFile().exists()
    }
}
