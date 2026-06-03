package com.qualityminds.lazyval.ksp

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import org.eclipse.collections.api.factory.Lists
import spock.lang.*

import java.nio.file.Path

@Title("KSP Generator Integration - Jackson")
class KspJacksonIT extends Specification {

    public static final Dependency dependencyJackson_2_Core = new Dependency("com.fasterxml.jackson.core", "jackson-core", "2.21.2")
    public static final Dependency dependencyJackson_2_Databind = new Dependency("com.fasterxml.jackson.core", "jackson-databind", "2.21.2")
    public static final Dependency dependencyJackson_3_Core = new Dependency("tools.jackson.core", "jackson-core", "3.1.0")
    public static final Dependency dependencyJackson_3_Databind = new Dependency("tools.jackson.core", "jackson-databind", "3.1.0")
    public static final String GENERATED_FILE_NAME_2 = "LazyvalJackson2Module.kt"
    public static final String GENERATED_FILE_NAME_3 = "LazyvalJacksonModule.kt"

    @TempDir()
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    @Unroll("#scenario.name() compiles and generated #expected.generatedFiles()")
    void "Jackson 2.x with all Scenarios"(){
        given:
        scenario.withDependencies(dependencyJackson_2_Databind, dependencyJackson_2_Core)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario << Scenario.Kotlin.all()
        expected = new Testresult.Kotlin.Success(GENERATED_FILE_NAME_2)
    }

    void "Jackson 2.x generated at correct default location when no override is given"(){
        given:
        def scenario = Scenario.Kotlin.quantity()
                .withDependencies(dependencyJackson_2_Databind, dependencyJackson_2_Core)

        when:
        testkitKotlin.run(projectDir, scenario)

        then: 'file is generated at correct location using base-package with generator-default'
        projectDir.resolve("build/generated/ksp/kotlin/test/$GENERATED_FILE_NAME_2").toFile().exists()
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
        projectDir.resolve("build/generated/ksp/kotlin/test/custom/$GENERATED_FILE_NAME_2").toFile().exists()
    }

    @Unroll("#scenario.name() compiles and generated #expected.generatedFiles()")
    void "Jackson 3.x with all Scenarios"(){
        given:
        scenario.withDependencies(dependencyJackson_3_Databind, dependencyJackson_3_Core)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario << Scenario.Kotlin.all()
        expected = new Testresult.Kotlin.Success(GENERATED_FILE_NAME_3)
    }

    void "Jackson 3.x generated at correct default location when no override is given"(){
        given:
        def scenario = Scenario.Kotlin.quantity()
                .withDependencies(dependencyJackson_3_Databind, dependencyJackson_3_Core)

        when:
        testkitKotlin.run(projectDir, scenario)

        then: 'file is generated at correct location using base-package with generator-default'
        projectDir.resolve("build/generated/ksp/kotlin/test/$GENERATED_FILE_NAME_3").toFile().exists()
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
        projectDir.resolve("build/generated/ksp/kotlin/test/custom/$GENERATED_FILE_NAME_3").toFile().exists()
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