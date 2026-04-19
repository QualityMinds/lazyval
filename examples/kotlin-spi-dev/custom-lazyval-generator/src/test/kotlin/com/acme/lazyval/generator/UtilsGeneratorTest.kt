package com.acme.lazyval.generator

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class UtilsGeneratorTest {

    @Test
    fun testGenerator(@TempDir tempDir: Path) {
        // GIVEN
        val kit = Testkit.kotlin()
        val scenario = Scenario.Kotlin.isbn()

        // WHEN
        val result: Testresult.Kotlin = kit.run(tempDir, scenario)

        // THEN
        val expected = Testresult.Kotlin.Success("Utils.kt")
        assertEquals(expected, result)
    }
}
