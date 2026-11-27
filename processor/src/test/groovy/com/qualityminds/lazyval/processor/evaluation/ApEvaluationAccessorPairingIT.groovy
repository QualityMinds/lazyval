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
 * AI evaluation finding P3 (GitHub issue "Evaluation: processor", label evaluation): a class's payload field is
 * paired with the first public method returning its type ({@code AccessorLookup.findAccessor}), not with its
 * own accessor.
 * <p>
 * This specification intentionally fails on main; it documents the finding until it is fixed. Expected: a field
 * is read through the accessor that belongs to it, and a field without an accessor of its own stays invisible to
 * Lazyval, as rules.adoc describes.
 */
@Title("Evaluation P3 - accessor pairing")
class ApEvaluationAccessorPairingIT extends Specification {

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    void "P3 the payload is read through its own accessor, not the first method returning the same type"() {
        given: 'Iban declares masked() before value(), both returning String'
        def scenario = Scenario.Java.ofSingle("scenarios/evaluation/p3/Iban.java")
                .withDependencies(ApJpaIT.dependencyJakartaPersistence)

        when:
        def result = testkitJava.run(projectDir, scenario)

        then:
        result == new Testresult.Java.Success("IbanAttributeConverter.java")

        and: 'the converter persists the payload, not its masked display form'
        def converter = testkitJava.generatedSourcePath(projectDir,
                "test/boundary/persistence/jpa/IbanAttributeConverter.java").toFile().text
        converter.contains("type.value()")
        !converter.contains("masked()")
    }

    void "P3 a second field without an accessor of its own does not count as a payload"() {
        given: 'Email derives a domain field that only value() could be paired with by type'
        def scenario = Scenario.Java.ofSingle("scenarios/evaluation/p3/Email.java")
                .withDependencies(ApJpaIT.dependencyJakartaPersistence)

        expect: 'value() belongs to value; domain has no public accessor and is invisible to Lazyval'
        testkitJava.run(projectDir, scenario) == new Testresult.Java.Success("EmailAttributeConverter.java")
    }
}
