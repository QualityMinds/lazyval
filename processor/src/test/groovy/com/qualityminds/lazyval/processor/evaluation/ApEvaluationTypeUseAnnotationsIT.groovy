package com.qualityminds.lazyval.processor.evaluation

import com.qualityminds.lazyval.processor.ApBeanValidationIT
import com.qualityminds.lazyval.processor.ApCassandraCodecIT
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
 * AI evaluation finding P9 (GitHub issue "Evaluation: processor", label evaluation): types are compared through
 * {@code TypeMirror.toString()}, which renders TYPE_USE annotations — {@code java.lang.@NotBlank String} is not
 * {@code java.lang.String}. A constrained field loses its accessor, and the Cassandra generator finds no
 * {@code TypeCodecs} constant and aborts javac with a {@code NullPointerException}.
 * <p>
 * This specification intentionally fails on main; it documents the finding until it is fixed. Expected: type
 * annotations are ignored when types are compared, so both domain-primitives generate as their unannotated
 * counterparts do.
 */
@Title("Evaluation P9 - TYPE_USE annotations on the payload")
class ApEvaluationTypeUseAnnotationsIT extends Specification {

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    void "P9 a field carrying a TYPE_USE constraint is paired with its accessor"() {
        given: '@NotBlank private final String value, read through public String value()'
        def scenario = Scenario.Java.ofSingle("scenarios/evaluation/p9/Name.java")
                .withDependencies(ApJpaIT.dependencyJakartaPersistence, ApBeanValidationIT.dependencyBeanValidation)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then: 'no "has no public accessor ... returning java.lang.@...NotBlank String" error'
        result instanceof Testresult.Java.Success
        (result as Testresult.Java.Success).generatedFiles().contains("NameAttributeConverter.java")
    }

    void "P9 a record component carrying a TYPE_USE constraint gets a Cassandra codec instead of crashing javac"() {
        given: 'record Mail(@NotBlank String value)'
        def scenario = Scenario.Java.ofSingle("scenarios/evaluation/p9/Mail.java")
                .withDependencies(ApCassandraCodecIT.dependencyDriverCore, ApBeanValidationIT.dependencyBeanValidation)

        when: 'on main, javac throws instead of returning a result'
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Success
        (result as Testresult.Java.Success).generatedFiles().contains("LazyvalCassandraCodecs.java")

        and: 'the payload is mapped like a plain String'
        testkitJava.generatedSourcePath(projectDir, "test/boundary/persistence/cassandra/LazyvalCassandraCodecs.java")
                .toFile().text.contains("TypeCodecs.TEXT")
    }
}
