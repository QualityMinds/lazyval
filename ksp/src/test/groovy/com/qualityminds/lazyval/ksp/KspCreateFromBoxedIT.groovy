package com.qualityminds.lazyval.ksp

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title

import java.nio.file.Path

/**
 * {@code element.java.createFromBoxed} against real declarations, through the test-only
 * {@link CreateFromBoxedProbeGenerator}: every domain-primitive is rebuilt in generated Java from an {@code Integer}.
 * <p>
 * Each one declares, next to the creator Lazyval matches, an overload javac would pick for the wrong argument
 * form, and that overload does not compile where the generated Java calls it. So the boxed payload has to be
 * unboxed where the matched creator takes {@code Int}, and passed as it is where it takes {@code Int?}, which
 * only a factory can: a constructor taking the payload as nullable is rejected. A Java creator taking an
 * {@code Integer} counts as the latter, although Kotlin sees its parameter as {@code Int}.
 */
@Title("KSP SPI - rebuilding from a boxed payload in Java")
class KspCreateFromBoxedIT extends Specification {

    @TempDir
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    void "a boxed payload is rebuilt through the matched #creator"() {
        given:
        def scenario = Scenario.Kotlin.of(creator, "scenarios/edge/CreateFromBoxedProbe.kt", source)
        if (library) {
            scenario.withJavaLibrary(library)
        }

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(generatedFile)

        where:
        creator                         | source                                       | library                          || generatedFile
        'constructor taking Int'        | 'scenarios/edge/BoxedConstructorOverload.kt' | null                             || 'BoxedConstructorOverloadProbe.java'
        'factory taking Int'            | 'scenarios/kotlin/Quantity.kt'               | null                             || 'QuantityProbe.java'
        'factory taking Int?'           | 'scenarios/edge/NullableFactoryPayload.kt'   | null                             || 'NullableFactoryPayloadProbe.java'
        'Java factory taking Integer'   | 'scenarios/edge/SharedPointsConfig.kt'       | 'scenarios/shared/Points.java'   || 'PointsProbe.java'
    }
}
