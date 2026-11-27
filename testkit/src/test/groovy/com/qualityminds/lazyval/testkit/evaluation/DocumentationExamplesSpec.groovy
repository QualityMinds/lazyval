package com.qualityminds.lazyval.testkit.evaluation

import spock.lang.Specification

/**
 * AI evaluation finding T14: the data-driven examples the SPI docs include do not compile. The Spock example
 * ({@code _documentation/modules/spi/examples/DataDrivenSpockIT.groovy}) uses {@code Testkit} and
 * {@code Testresult} without importing them. Its Kotlin twin ({@code DataDrivenKotestIT.kt}, Groovy
 * {@code def}, {@code shouldEqual}) is not covered here: checking it would need kotest on this classpath.
 * <p>
 * This test intentionally fails on {@code main}. It compiles the example as it is published, against the
 * test classpath of this module, which has the Testkit, Spock and Groovy on it.
 */
class DocumentationExamplesSpec extends Specification {

    private static final String SPOCK_EXAMPLE = '_documentation/modules/spi/examples/DataDrivenSpockIT.groovy'

    void "T14 the Spock example in the SPI docs compiles against the Testkit"() {
        given:
        def example = locateInRepository(SPOCK_EXAMPLE)

        when:
        new GroovyClassLoader(getClass().classLoader).withCloseable { it.parseClass(example) }

        then:
        noExceptionThrown()
    }

    /** Surefire runs in the module directory; the docs sit next to it at the repository root. */
    private static File locateInRepository(String relativePath) {
        def dir = new File('.').absoluteFile
        for (int depth = 0; dir != null && depth < 4; depth++) {
            def candidate = new File(dir, relativePath)
            if (candidate.isFile()) {
                return candidate
            }
            dir = dir.parentFile
        }
        throw new FileNotFoundException("$relativePath not found above ${new File('.').absolutePath}")
    }
}
