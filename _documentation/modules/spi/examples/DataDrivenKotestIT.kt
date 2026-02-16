package com.acme

import de.qualityminds.lazyval.testkit.dependencies.Dependency
import de.qualityminds.lazyval.testkit.scenarios.Scenario
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class DataDrivenKotestIT : DescribeSpec({

    describe("Generator working as expected for all predefined scenarios") {
        val dependencyNeededByGenerator = Dependency("groupdId", "artifactId", "version")
        withData(
            nameFn = { "${it.name()} successfully generates classes" },
            Scenario.Kotlin.All // 1.
        ){ scenario ->
            val tempDir = Files.createTempDirectory("test")
            def kit = Testkit.kotlin()
            scenario.withDependencies(dependencyNeededByGenerator) // 2.

            val expectedFile = scenario.name().replace(".kt", "Gen.kt") // 3.
            val expected = Testresult.Kotlin.Success(expectedFile) // 4.
            kit.run(tempDir, scenario) shouldEqual expected // 5.
        }
    }
})