package com.qualityminds.lazyval.processor.evaluation

import com.qualityminds.lazyval.processor.ApJpaIT
import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title

import java.nio.file.Path

/**
 * AI evaluation finding P18 (GitHub issue "Evaluation: processor", label evaluation): identifiers taken from the
 * user's source end up inside JavaPoet format strings, so a {@code $} in an accessor name is read as a format
 * placeholder and JavaPoet throws {@code IllegalArgumentException}, aborting javac.
 * <p>
 * This specification intentionally fails on main; it documents the finding until it is fixed. Expected: a legal
 * Java identifier containing {@code $} is emitted verbatim.
 */
@Title("Evaluation P18 - \$ in identifiers")
class ApEvaluationDollarIdentifierIT extends Specification {

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    void "P18 a payload accessor whose name contains a dollar sign generates code calling it"() {
        given: 'record Tag(String $name)'
        def scenario = Scenario.Java.ofSingle("scenarios/evaluation/p18/Tag.java")
                .withDependencies(ApJpaIT.dependencyJakartaPersistence)

        when: 'on main, javac throws: IllegalArgumentException from JavaPoet'
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success("TagAttributeConverter.java")
        testkitJava.generatedSourcePath(projectDir, "test/boundary/persistence/jpa/TagAttributeConverter.java")
                .toFile().text.contains('type.$name()')
    }
}
