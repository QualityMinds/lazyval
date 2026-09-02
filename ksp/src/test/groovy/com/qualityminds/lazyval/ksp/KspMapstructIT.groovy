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

@Title("KSP Generator Integration - Mapstruct")
class KspMapstructIT extends Specification {

    public static final Dependency dependencyJakartaAnnotations = new Dependency("jakarta.annotation", "jakarta.annotation-api", "3.0.0")
    public static final Dependency dependencyMapstruct = new Dependency("org.mapstruct", "mapstruct", "1.6.3")
    public static final String GENERATED_FILE_NAME = "LazyvalMapper.java"

    @TempDir()
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    void "Mapstruct with combined Scenarios"() {
        given: 'a compiler run with all sources'
        def scenario = Scenario.Kotlin.combined()
                .withDependencies(dependencyMapstruct, dependencyJakartaAnnotations)

        and: 'a defined approval for the generated mapper'
        List<Approval.ForKotlin> approvals = [
                Approval.JavaSource.at("test/$GENERATED_FILE_NAME", "approvals/mapstruct/$GENERATED_FILE_NAME")
        ]

        when:
        def result = testkitKotlin.run(projectDir, scenario, approvals)

        then:
        result == Testresult.Kotlin.Approved.of(approvals)
    }

    /**
     * Pins the four ways a member's JVM name can part company with its Kotlin declaration, since only
     * the Java half of the output has to care: `@JvmName` on a getter or on a factory renames it, and a
     * companion factory without `@JvmStatic` is not on the type at all — it is reached through the
     * companion field, under the companion's own name when it has one. The last two are `internal` as
     * well, so the fixture is also where a leaked `$module` suffix would show up.
     *
     * KspIT already proves these compile. What an approval adds is that they compile for the right
     * reason: a wrong-but-resolvable name would satisfy javac just as well.
     */
    void "Mapstruct resolves JVM names that differ from the Kotlin declaration"() {
        given: 'one domain-primitive per way a JVM name can be moved'
        def scenario = Scenario.Kotlin.of("mapstruct-jvm-names",
                "scenarios/edge/PropertyWithRenamedJvmName.kt",
                "scenarios/edge/FactoryWithRenamedJvmName.kt",
                "scenarios/edge/FactoryWithoutJvmStatic.kt",
                "scenarios/edge/FactoryOnNamedCompanion.kt",
                "scenarios/edge/InternalFactoryWithJvmName.kt",
                "scenarios/edge/InternalFactoryOnCompanion.kt")
                .withDependencies(dependencyMapstruct, dependencyJakartaAnnotations)

        and: 'a defined approval for the generated mapper'
        List<Approval.ForKotlin> approvals = [
                Approval.JavaSource.at("test/$GENERATED_FILE_NAME", "approvals/mapstruct/LazyvalMapperJvmNames.java")
        ]

        when:
        def result = testkitKotlin.run(projectDir, scenario, approvals)

        then:
        result == Testresult.Kotlin.Approved.of(approvals)
    }

    void "Warning is issued when package is not configured in any way"(){
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(dependencyMapstruct)
        and: 'no base-package nor generator-package is configured '
        scenario.withDisabledBasePackage()

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'warning is issued'
        def expectedWarning = "Lazyval: Neither configuration for 'lazyval.generators.basePackage' nor 'lazyval.mapstruct.package' is set. Falling back to package of first element: 'scenarios.kotlin'"
        result == new Testresult.Kotlin.SuccessWithWarnings(Lists.immutable.of(GENERATED_FILE_NAME), Lists.immutable.of(expectedWarning))
    }

    void "Package override by Generator works as expected"(){
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(dependencyMapstruct)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.mapstruct.package", "test.custom")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME)

        and: 'file is at correct package'
        testkitKotlin.generatedJavaSourcePath(projectDir, "test/custom/$GENERATED_FILE_NAME").toFile().exists()
    }

    void "Does not add '@Generated' when jakarta.annotations-api not on classpath"(){
        given:
        def scenario = Scenario.Kotlin.quantity().withDependencies(dependencyMapstruct)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(GENERATED_FILE_NAME)

        and: 'doesnt contain @Generated'
        !Files.readString(testkitKotlin.generatedJavaSourcePath(projectDir, "test/$GENERATED_FILE_NAME")).contains("@Generated")
    }
}