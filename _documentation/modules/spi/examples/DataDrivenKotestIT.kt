package com.acme

import de.qualityminds.lazyval.testkit.dependencies.Dependency
import de.qualityminds.lazyval.testkit.scenarios.Scenario
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class DataDrivenKotestIT : DescribeSpec({

    describe("Generator working as expected for all predefined scenarios") {
        val dependencyMapstruct = Dependency("org.mapstruct", "mapstruct", "1.6.3")
        withData(
            nameFn = { "${it.name()} successfully generates LazyvalMapper" },
            Scenario.Kotlin.All // 1.
        ){ scenario ->
            val tempDir = Files.createTempDirectory("test")
            def kit = Testkit.kotlin()
            scenario.withDependencies(dependencyMapstruct) // 2.

            val expected = Testresult.Kotlin.Success("LazyvalMapper.java") // 3.
            kit.run(tempDir, scenario) shouldEqual expected // 4.
        }
    }
})