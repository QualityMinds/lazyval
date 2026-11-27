package com.qualityminds.lazyval.processor.evaluation

import com.qualityminds.lazyval.processor.ApJpaIT
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
 * AI evaluation finding P17 (GitHub issue "Evaluation: processor", label evaluation): a package option is used
 * without checking that it is a valid package name, so {@code -Alazyval.jpa.package=com.acme-x} reaches
 * {@code Filer.createSourceFile} and javac aborts with an uncaught {@code FilerException: Illegal name}.
 * <p>
 * This specification intentionally fails on main; it documents the finding until it is fixed. Expected: a Lazyval
 * error naming the option and its invalid value.
 */
@Title("Evaluation P17 - invalid package options")
class ApEvaluationIllegalPackageOptionIT extends Specification {

    @TempDir()
    Path projectDir

    @Shared
    def testkitJava = Testkit.java()

    @Unroll("P17 -A#option=#value is reported as a Lazyval error")
    void "P17 a package option that is no valid package name is reported instead of crashing javac"() {
        given: "the option is the only package configuration (the testkit's default base package would replace one)"
        def scenario = Scenario.Java.quantity().withDependencies(ApJpaIT.dependencyJakartaPersistence)
                .withDisabledBasePackage()
                .withOption(option, value)

        when: 'on main, javac throws: FilerException: Illegal name'
        def result = testkitJava.run(projectDir, scenario)

        then:
        result instanceof Testresult.Java.Failure
        (result as Testresult.Java.Failure).errors().any {
            it.startsWith("Lazyval:") && it.contains(option) && it.contains(value)
        }

        where:
        option                          | value
        "lazyval.jpa.package"           | "com.acme-x"
        "lazyval.generators.basePackage" | "com.acme-x"
    }
}
