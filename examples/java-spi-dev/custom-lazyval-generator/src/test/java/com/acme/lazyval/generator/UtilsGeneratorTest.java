package com.acme.lazyval.generator;

import com.qualityminds.lazyval.testkit.Testkit;
import com.qualityminds.lazyval.testkit.Testresult;
import com.qualityminds.lazyval.testkit.scenarios.Scenario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UtilsGeneratorTest {

    @Test
    public void testGenerator(@TempDir Path tempDir) {
        // GIVEN
        var kit = Testkit.java();
        var scenario = Scenario.Java.Isbn;

        // WHEN
        Testresult.Java result = kit.run(tempDir, scenario);

        // THEN
        var expected = new Testresult.Java.Success("IsbnUtils.java", "Utils.java");
        assertEquals(expected, result);
    }
}
