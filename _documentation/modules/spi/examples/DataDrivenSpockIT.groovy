package com.acme

import de.qualityminds.lazyval.testkit.dependencies.Dependency
import de.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.file.Path

class DataDrivenSpockIT extends Specification {

    public static final Dependency DependencyNeededByGenerator = new Dependency("groupdId", "artifactId", "version")

    @TempDir()
    Path tempDir

    @Unroll("#scenario.name() successfully generates #expectedFile")
    void "Generator working as expected for all predefined scenarios"(){
        given:
        def kit = Testkit.java()
        scenario.withDependencies(DependencyNeededByGenerator) // 2.

        expect:
        kit.run(tempDir, scenario) == expected // 5.

        where:
        scenario << Scenario.Java.All // 1.
        expectedFile = scenario.name().replace(".java", "Gen.java") // 3.
        expected = new Testresult.Java.Success(expectedFile) // 4.
    }
}