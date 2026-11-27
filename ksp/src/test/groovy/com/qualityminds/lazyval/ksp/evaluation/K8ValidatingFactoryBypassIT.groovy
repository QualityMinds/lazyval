package com.qualityminds.lazyval.ksp.evaluation

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

/**
 * AI evaluation finding K8 (GitHub issue "Evaluation: ksp", label evaluation). This specification intentionally
 * fails on main: it documents the expected behaviour, not the current one.
 * <p>
 * A factory is where a class validates what it is built from, which is why Lazyval prefers it over a reachable
 * constructor. Two shapes slip past that rule on main, and generated code constructs around the check:
 * <ul>
 *     <li>a factory the companion inherits from a base class: {@code CreatorLookup.findFactories} scans the
 *     companion's declared functions only;</li>
 *     <li>an {@code internal} factory of a value-class payload: {@code valueClassFactories} keeps public functions
 *     only, so the value class is rebuilt through its {@code internal} constructor although the factory is just
 *     as reachable from code generated into this module.</li>
 * </ul>
 */
@Title("Evaluation K8 - validating factories bypassed")
class K8ValidatingFactoryBypassIT extends Specification {

    static final Dependency JPA = new Dependency("jakarta.persistence", "jakarta.persistence-api", "3.2.0")
    static final Dependency MAPSTRUCT = new Dependency("org.mapstruct", "mapstruct", "1.6.3")

    @TempDir
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    @Unroll("K8 #type is rebuilt through the factory its companion inherits (#generatedFile)")
    void "K8 a factory inherited by the companion is preferred over the constructor"() {
        given:
        def scenario = Scenario.Kotlin.ofSingle("scenarios/evaluation/k8/InheritedFactory.kt")
                .withDependencies(JPA, MAPSTRUCT)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Success

        and: 'the generated code calls of(...) and never the constructor'
        def generated = Files.readString(generatedFile.endsWith('.java')
                ? testkitKotlin.generatedJavaSourcePath(projectDir, generatedFile)
                : testkitKotlin.generatedKotlinSourcePath(projectDir, generatedFile))
        !(generated =~ constructorCall)
        generated =~ /\b${type}(\.Companion)?\.of\(/

        where:
        type        | generatedFile                                                | constructorCall
        'OrderId'   | 'test/boundary/persistence/jpa/OrderIdAttributeConverter.kt'   | /\bOrderId\(it\)/
        'OrderId'   | 'test/LazyvalMapper.java'                                      | /new OrderId\(/
        'InvoiceId' | 'test/boundary/persistence/jpa/InvoiceIdAttributeConverter.kt' | /\bInvoiceId\(it\)/
        'InvoiceId' | 'test/LazyvalMapper.java'                                      | /new InvoiceId\(/
    }

    @Unroll("K8 a value-class payload is re-wrapped through its internal factory (#generatedFile)")
    void "K8 an internal validating factory of a value-class payload is preferred over its internal constructor"() {
        given:
        def scenario = Scenario.Kotlin.ofSingle("scenarios/evaluation/k8/InternalValueClassFactory.kt")
                .withDependencies(JPA, MAPSTRUCT)

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result instanceof Testresult.Kotlin.Success

        and: 'Rate is rebuilt through Rate.of, which enforces the range'
        def generated = Files.readString(testkitKotlin.generatedKotlinSourcePath(projectDir, generatedFile))
        !(generated =~ /\bRate\(/)
        generated =~ /\bRate(\.Companion)?\.of\(/

        where:
        generatedFile << [
                // read by the generated Java, which cannot see the value class at all
                'evaluation/k8/DiscountJvmAccess.kt',
                'test/boundary/persistence/jpa/DiscountAttributeConverter.kt'
        ]
    }
}
