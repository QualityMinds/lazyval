package com.qualityminds.lazyval.ksp

import com.qualityminds.lazyval.testkit.Approval
import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import org.eclipse.collections.api.factory.Lists
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title

import java.nio.file.Files
import java.nio.file.Path

@Title("KSP Generator Integration - Jackson")
class KspJacksonIT extends Specification {

    public static final Dependency dependencyJakartaAnnotations = new Dependency("jakarta.annotation", "jakarta.annotation-api", "3.0.0")
    public static final Dependency dependencyJackson_2_Core = new Dependency("com.fasterxml.jackson.core", "jackson-core", "2.21.2")
    public static final Dependency dependencyJackson_2_Databind = new Dependency("com.fasterxml.jackson.core", "jackson-databind", "2.21.2")
    public static final Dependency dependencyJackson_3_Core = new Dependency("tools.jackson.core", "jackson-core", "3.1.0")
    public static final Dependency dependencyJackson_3_Databind = new Dependency("tools.jackson.core", "jackson-databind", "3.1.0")
    public static final String GENERATED_FILE_NAME_2 = "LazyvalJackson2Module.kt"
    public static final String GENERATED_FILE_NAME_3 = "LazyvalJacksonModule.kt"
    private static final String GENERATED_SERVICELOADER_JACKSON_2 = "com.fasterxml.jackson.databind.Module"
    private static final String GENERATED_SERVICELOADER_JACKSON_3 = "tools.jackson.databind.JacksonModule"

    @TempDir()
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    void "Jackson 2.x with combined Scenarios"(){
        given: 'a compiler run with all sources'
        def scenario = Scenario.Kotlin.combined().withDependencies(dependencyJackson_2_Databind, dependencyJackson_2_Core)

        and: 'a defined approval for source and service-loader'
        List<Approval> approvals = [
                Approval.KotlinSource.at("test/LazyvalJackson2Module.kt", "approvals/jackson/LazyvalJackson2Module.kt"),
                Approval.ServiceLoader.of(GENERATED_SERVICELOADER_JACKSON_2, "test.LazyvalJackson2Module")
        ]

        when:
        def result = testkitKotlin.run(projectDir, scenario, approvals)

        then:
        result == Testresult.Kotlin.Approved.of(approvals)
    }


    void "Jackson 2.x package override by Generator works as expected"(){
        given:
        def scenario = Scenario.Kotlin.quantity()
                .withDependencies(dependencyJackson_2_Databind, dependencyJackson_2_Core)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.jackson.package", "test.custom")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME_2)

        and: 'file is at correct package'
        testkitKotlin.generatedKotlinSourcePath(projectDir, "test/custom/$GENERATED_FILE_NAME_2").toFile().exists()
    }

    void "Jackson 2.x does not add '@Generated' when jakarta.annotations-api not on classpath"(){
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(dependencyJackson_2_Core, dependencyJackson_2_Databind)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME_2)

        and: 'doesnt contain @Generated'
        !Files.readString(testkitKotlin.generatedKotlinSourcePath(projectDir, "test/$GENERATED_FILE_NAME_2")).contains("@Generated")
    }

    void "Jackson 3.x with combined Scenarios"(){
        given:
        def scenario = Scenario.Kotlin.combined().withDependencies(dependencyJackson_3_Databind, dependencyJackson_3_Core)

        and: 'a defined approval for source and service-loader'
        List<Approval> approvals = [
                Approval.KotlinSource.at("test/LazyvalJacksonModule.kt", "approvals/jackson/LazyvalJacksonModule.kt"),
                Approval.ServiceLoader.of(GENERATED_SERVICELOADER_JACKSON_3, "test.LazyvalJacksonModule")
        ]

        when:
        def result = testkitKotlin.run(projectDir, scenario, approvals)

        then:
        result == Testresult.Kotlin.Approved.of(approvals)
    }

    void "Jackson 3.x package override by Generator works as expected"(){
        given:
        def scenario = Scenario.Kotlin.quantity()
                .withDependencies(dependencyJackson_3_Databind, dependencyJackson_3_Core)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.jackson.package", "test.custom")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME_3)

        and: 'file is at correct package'
        testkitKotlin.generatedKotlinSourcePath(projectDir, "test/custom/$GENERATED_FILE_NAME_3").toFile().exists()
    }

    void "Jackson 3.x does not add '@Generated' when jakarta.annotations-api not on classpath"(){
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(dependencyJackson_3_Core, dependencyJackson_3_Databind)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME_3)

        and: 'doesnt contain @Generated'
        !Files.readString(testkitKotlin.generatedKotlinSourcePath(projectDir, "test/$GENERATED_FILE_NAME_3")).contains("@Generated")
    }

    void "When Jackson 2 and 3 are active are warning is issued"(){
        given:
        def scenario = Scenario.Kotlin.quantity()
                .withDependencies(
                        dependencyJackson_2_Databind, dependencyJackson_2_Core,
                        dependencyJackson_3_Databind, dependencyJackson_3_Core)
        def expectedWarning = "Lazyval: Both 'jackson-2' and 'jackson-3' generators are active (probably due to transitive dependencies). " +
                "This might be intentional, then ignore this warning. " +
                "Otherwise, disable via one 'lazyval.generators.disable'"
        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'no warning is issued'

        result == new Testresult.Kotlin.SuccessWithWarnings(Lists.immutable.of(GENERATED_FILE_NAME_2, GENERATED_FILE_NAME_3), Lists.immutable.of(expectedWarning))
    }
}