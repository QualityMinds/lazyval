package com.qualityminds.lazyval.ksp.evaluation

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * AI evaluation finding T8: the Kotlin toolchain tells KSP to target JVM 17 ({@code KspStep}) but starts
 * kotlinc without {@code -jvm-target} ({@code KotlinCompileStep}), so kotlinc falls back to its own default
 * target. Sources that need a newer target than that default, such as a {@code @JvmRecord data class},
 * pass KSP and then fail to compile, although Lazyval's baseline is Java 17.
 * <p>
 * This test intentionally fails on {@code main}.
 */
class KotlinJvmTargetIT extends Specification {

    @TempDir
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    void "T8 kotlinc compiles for the same JVM target as KSP"() {
        when: 'a scenario declares a JVM record, which needs -jvm-target 16 or later'
        def result = testkitKotlin.run(projectDir, Scenario.Kotlin.ofSingle('scenarios/evaluation/JvmRecordEmail.kt'))

        then: 'it compiles; without a generator dependency nothing is generated'
        result == new Testresult.Kotlin.NothingGenerated()
    }
}
