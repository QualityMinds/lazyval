package com.qualityminds.lazyval.processor.evaluation

import com.qualityminds.lazyval.processor.ApCassandraCodecIT
import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title
import spock.lang.Unroll

import java.nio.file.Path

/**
 * AI evaluation finding P13 (GitHub issue "Evaluation: processor", label evaluation): the Cassandra generator looks
 * a payload type up with {@code Objects.requireNonNull(TYPE_CODEC_MAP.get(...))}
 * ({@code CassandraCodecGenerator.resolveTypeCodecConstant}), so one domain-primitive wrapping an unmapped type
 * aborts the whole javac run with a {@code NullPointerException}.
 * <p>
 * This specification intentionally fails on main; it documents the finding until it is fixed. Expected, as the
 * Cassandra generator documentation states: such domain-primitives are skipped (optionally with a warning), and
 * the others still get their codecs.
 */
@Title("Evaluation P13 - Cassandra payload types outside the mapping table")
class ApEvaluationCassandraUnmappedPayloadIT extends Specification {

    private static final String CODECS = "test/boundary/persistence/cassandra/LazyvalCassandraCodecs.java"

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    @Unroll("P13 a domain-primitive wrapping #payload is skipped instead of aborting the build")
    void "P13 the Cassandra generator skips payload types outside its mapping table"() {
        given: 'Quantity wraps a mapped int, the other domain-primitive an unmapped type'
        def scenario = Scenario.Java.of("p13 $payload", "scenarios/java/Quantity.java", *sources)
                .withDependencies(ApCassandraCodecIT.dependencyDriverCore)

        when: 'on main, javac throws: No TypeCodecs constant mapping for payload type'
        def result = testkitJava.run(projectDir, scenario)

        then: 'the build succeeds, optionally warning about the skipped type'
        result instanceof Testresult.Java.Success || result instanceof Testresult.Java.SuccessWithWarnings

        and: 'codecs are generated for the mapped type only'
        def codecs = testkitJava.generatedSourcePath(projectDir, CODECS).toFile().text
        codecs.contains("QuantityCodec")
        !codecs.contains(skipped)

        where:
        payload   | sources                                                                            | skipped
        "a char"  | ["scenarios/evaluation/p13/Grade.java"]                                            | "scenarios.evaluation.p13.Grade"
        "an enum" | ["scenarios/evaluation/p13/Level.java", "scenarios/evaluation/p13/Severity.java"] | "scenarios.evaluation.p13.Level"
    }
}
