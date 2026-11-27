package com.qualityminds.lazyval.testkit.evaluation

import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Specification

/**
 * AI evaluation finding T11: {@code ScenarioFactory.build()} merges its convenience settings into the
 * same map that {@code withOption} writes to, and it does so destructively. A user's own option for the
 * base package or the disable list is overwritten, the factory remembers what an earlier {@code build()}
 * put there, and {@code withDependencies} replaces instead of adding.
 * <p>
 * These tests intentionally fail on {@code main}; each feature documents one symptom of the finding.
 * Only {@code build().desc()} is inspected, no toolchain runs.
 */
class ScenarioFactoryOptionsSpec extends Specification {

    private static final String BASE_PACKAGE = 'lazyval.generators.basePackage'
    private static final String DISABLE = 'lazyval.generators.disable'

    void "T11 build keeps a base package the user set through withOption"() {
        given:
        def factory = Scenario.Java.isbn()
                .withOption(BASE_PACKAGE, 'com.acme')

        when:
        def options = factory.build().desc().options()

        then: 'the explicit option is not replaced by the default base package'
        options.get(BASE_PACKAGE) == 'com.acme'
    }

    void "T11 build keeps a disable list the user set through withOption next to withDisabledGenerators"() {
        given:
        def factory = Scenario.Java.isbn()
                .withOption(DISABLE, 'jackson-2')
                .withDisabledGenerators('mapstruct')

        when:
        def options = factory.build().desc().options()

        then: 'both ways of disabling a generator take effect'
        options.get(DISABLE).split(',') as Set == ['jackson-2', 'mapstruct'] as Set
    }

    void "T11 build does not change the factory it is called on"() {
        given: 'a factory that has been built once'
        def factory = Scenario.Java.isbn()
        factory.build()

        when: 'the default base package is disabled afterwards'
        factory.withDisabledBasePackage()
        def options = factory.build().desc().options()

        then: 'the second scenario carries no base package'
        !options.containsKey(BASE_PACKAGE)
    }

    void "T11 an empty withDisabledGenerators re-enables generators disabled before an earlier build"() {
        given: 'a factory built once with a disabled generator'
        def factory = Scenario.Java.isbn()
                .withDisabledGenerators('jpa')
        factory.build()

        when: 'the disabled generators are reset'
        factory.withDisabledGenerators()
        def options = factory.build().desc().options()

        then: 'nothing is disabled any more'
        !options.containsKey(DISABLE)
    }

    void "T11 withDependencies adds to the dependencies configured before"() {
        given:
        def first = new Dependency('com.example', 'first', '1.0')
        def second = new Dependency('com.example', 'second', '2.0')

        when:
        def dependencies = Scenario.Java.isbn()
                .withDependencies(first)
                .withDependencies(second)
                .build().desc().dependencies()

        then: 'the javadoc promises additional dependencies, so both are on the classpath'
        dependencies.toList() == [first, second]
    }
}
