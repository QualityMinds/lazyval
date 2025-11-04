package com.acme

import de.qualityminds.lazyval.testkit.dependencies.Dependency
import de.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.file.Path

class DataDrivenSpockIT extends Specification {

    public static final Dependency DependencyMapstruct = new Dependency("org.mapstruct", "mapstruct", "1.6.3")

    @TempDir()
    Path tempDir

    @Unroll("#scenario.name() successfully generates LazyvalMapper")
    void "Generator working as expected for all predefined scenarios"(){
        given:
        def kit = Testkit.java()
        scenario.withDependencies(DependencyMapstruct) // 2.

        expect:
        kit.run(tempDir, scenario) == expected // 4.

        where:
        scenario << Scenario.Java.All // 1.
        expected = new Testresult.Java.Success("LazyvalMapper.java") // 3.
    }
}