package com.qualityminds.lazyval.testkit.internal.toolchain.kotlin

import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Pins that {@link KotlinToolchain#MODULE_NAME} reaches kotlinc and not just KSP.
 *
 * Kotlin mangles the JVM name of an {@code internal} member to {@code name$module}, so the two steps
 * disagreeing means KSP reports JVM names that never appear in the bytecode kotlinc emits. The failure
 * mode is silent in both directions: KSP takes the name through {@code KSPJvmConfig}, kotlinc through a
 * {@code -module-name} flag that simply defaults to {@code main} when nobody passes it.
 *
 * {@code META-INF/<module>.kotlin_module} is the observable, because kotlinc names that descriptor after
 * the module it believes it is compiling.
 *
 * Drives {@link KotlinToolchain} directly because {@code Testkit.kotlin()} cannot start from this
 * module: it requires Lazyval's symbol processor on the classpath, and that lives downstream in
 * {@code ksp}. Testing it from there instead would mean publishing the module name and the class-output
 * root on the {@code Testkit} API to reach facts that are this package's own business.
 */
class ModuleNameSpec extends Specification {

    @TempDir
    Path projectDir

    void "Kotlinc compiles under the same module name KSP is given"() {
        given: 'any Kotlin source, since only the module descriptor is under test'
        def scenario = Scenario.Kotlin.isbn().build()

        when: 'closing releases the classloader KSP opened, so a repeat run is not blocked by the handle'
        def toolchain = KotlinToolchain.create(getClass().classLoader, projectDir, scenario.desc())
        def result = toolchain.withCloseable { it.run() }

        then: 'kotlinc ran, so there is a module name to check'
        result.stepOutcomes()[Step.KOTLINC] == StepOutcome.SUCCESS

        and: 'and it is the one KSP was told'
        def metaInf = projectDir.resolve("build/classes/META-INF")
        Files.exists(metaInf.resolve("${KotlinToolchain.MODULE_NAME}.kotlin_module"))

        and: 'rather than the default nobody chose'
        !Files.exists(metaInf.resolve("main.kotlin_module"))
    }
}
