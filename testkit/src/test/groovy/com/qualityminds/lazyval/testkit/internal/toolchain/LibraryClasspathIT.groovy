package com.qualityminds.lazyval.testkit.internal.toolchain

import com.qualityminds.lazyval.testkit.internal.toolchain.java.JavaToolchain
import com.qualityminds.lazyval.testkit.internal.toolchain.kotlin.KotlinToolchain
import com.qualityminds.lazyval.testkit.internal.toolchain.kotlin.Step
import com.qualityminds.lazyval.testkit.internal.toolchain.kotlin.StepOutcome
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Pins that both toolchains put a scenario's {@code withJavaLibrary} classes on the classpath, and only there:
 * the library's types resolve, but are not compiled into the scenario's own output, as a shared module's jar
 * would not be.
 *
 * Drives the toolchains directly, with no processor, because {@code Testkit.java()} and {@code Testkit.kotlin()}
 * require Lazyval's processors and those live downstream (see {@code ModuleNameSpec}). Without one, the Kotlin
 * toolchain's KSP and javac steps have nothing to resolve the library against, so only kotlinc is observed here;
 * the downstream {@code KspCreateFromBoxedIT} and {@code KspMapstructIT} cover the other two.
 */
class LibraryClasspathIT extends Specification {

    private static final String LIBRARY = 'library/shared/Distance.java'

    @TempDir
    Path projectDir

    void "Javac resolves the library's types, which stay out of the scenario's classes"() {
        given:
        def scenario = Scenario.Java.ofSingle('library/Route.java').withJavaLibrary(LIBRARY).build()

        when:
        def result = JavaToolchain.create(projectDir, scenario.desc()).withCloseable { it.run() }

        then:
        result.isSuccessful()

        and:
        def classes = JavaToolchain.classOutputDir(projectDir)
        Files.exists(classes.resolve('library/Route.class'))
        !Files.exists(classes.resolve('library/shared/Distance.class'))
    }

    void "Javac does not find the library's types in a scenario without the library"() {
        given: 'the control for the feature above: Route compiles only because of the library'
        def scenario = Scenario.Java.ofSingle('library/Route.java').build()

        when:
        def result = JavaToolchain.create(projectDir, scenario.desc()).withCloseable { it.run() }

        then:
        !result.isSuccessful()
        result.errors.any { it.contains('library.shared') }
    }

    void "Kotlinc resolves the library's types, which stay out of the scenario's classes"() {
        given:
        def scenario = Scenario.Kotlin.ofSingle('library/Route.kt').withJavaLibrary(LIBRARY).build()

        when:
        def result = KotlinToolchain.create(getClass().classLoader, projectDir, scenario.desc()).withCloseable { it.run() }

        then:
        result.stepOutcomes()[Step.KOTLINC] == StepOutcome.SUCCESS

        and:
        def classes = projectDir.resolve('build/classes')
        Files.exists(classes.resolve('library/Route.class'))
        !Files.exists(classes.resolve('library/shared/Distance.class'))
    }

    void "Kotlinc does not find the library's types in a scenario without the library"() {
        given: 'the control for the feature above: Route compiles only because of the library'
        def scenario = Scenario.Kotlin.ofSingle('library/Route.kt').build()

        when:
        def result = KotlinToolchain.create(getClass().classLoader, projectDir, scenario.desc()).withCloseable { it.run() }

        then:
        result.stepOutcomes()[Step.KOTLINC] == StepOutcome.COMPILE_ERROR
    }
}
