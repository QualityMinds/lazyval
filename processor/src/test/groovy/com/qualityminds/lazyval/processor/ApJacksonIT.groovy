package com.qualityminds.lazyval.processor

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import org.eclipse.collections.api.factory.Lists
import spock.lang.*

import java.nio.file.Path

@Title("Generator Integration - Jackson")
class ApJacksonIT extends Specification {

    public static final Dependency dependencyJackson_2_Core = new Dependency("com.fasterxml.jackson.core", "jackson-core", "2.21.2")
    public static final Dependency dependencyJackson_2_Databind = new Dependency("com.fasterxml.jackson.core", "jackson-databind", "2.21.2")
    public static final Dependency dependencyJackson_3_Core = new Dependency("tools.jackson.core", "jackson-core", "3.1.0")
    public static final Dependency dependencyJackson_3_Databind = new Dependency("tools.jackson.core", "jackson-databind", "3.1.0")

    private static final String GENERATED_FILE_NAME = "LazyvalJacksonModule.java"

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    @Unroll("#scenario.name() compiles and generated #expected.generatedFiles()")
    void "Jackson 2.x with all Scenarios"(){
        given:
        scenario.withDependencies(dependencyJackson_2_Databind, dependencyJackson_2_Core)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == expected

        where:
        scenario << Scenario.Java.all()
        expected = new Testresult.Java.Success("LazyvalJacksonModule.java")
    }

    void "Jackson 2.x generated at correct default location when no override is given"(){
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencyJackson_2_Databind, dependencyJackson_2_Core)

        when:
        testkitJava.run(projectDir, scenario)

        then: 'file is generated at correct location using base-package with generator-default'
        projectDir.resolve("build/generated/test/$GENERATED_FILE_NAME").toFile().exists()
    }

    void "Jackson 2.x package override by generator works as expected"(){
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencyJackson_2_Databind, dependencyJackson_2_Core)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.jackson.package", "test.custom")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Java.Success(Lists.immutable.of(GENERATED_FILE_NAME))

        and: 'file is at correct package'
        projectDir.resolve("build/generated/test/custom/$GENERATED_FILE_NAME").toFile().exists()
    }

    @Unroll("#scenario.name() compiles and generated #expected.generatedFiles()")
    void "Jackson 3.x with all Scenarios"(){
        given:
        scenario.withDependencies(dependencyJackson_3_Databind, dependencyJackson_3_Core)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == expected

        and: 'file is generated at correct location using base-package with generator-default'
        projectDir.resolve("build/generated/test/$GENERATED_FILE_NAME").toFile().exists()

        where:
        scenario << Scenario.Java.all()
        expected = new Testresult.Java.Success(GENERATED_FILE_NAME)
    }

    void "Jackson 3.x generated at correct default location when no override is given"(){
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencyJackson_3_Databind, dependencyJackson_3_Core)

        when:
        testkitJava.run(projectDir, scenario)

        then: 'file is generated at correct location using base-package with generator-default'
        projectDir.resolve("build/generated/test/$GENERATED_FILE_NAME").toFile().exists()
    }

    void "Jackson 3.x package override by generator works as expected"(){
        given:
        def scenario = Scenario.Java.quantity()
                .withDependencies(dependencyJackson_3_Databind, dependencyJackson_3_Core)
        and: 'generator-package is overridden'
        scenario.withOption("lazyval.jackson.package", "test.custom")

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'no warning is issued'
        result == new Testresult.Java.Success(Lists.immutable.of(GENERATED_FILE_NAME))

        and: 'file is at correct package'
        projectDir.resolve("build/generated/test/custom/$GENERATED_FILE_NAME").toFile().exists()
    }
}