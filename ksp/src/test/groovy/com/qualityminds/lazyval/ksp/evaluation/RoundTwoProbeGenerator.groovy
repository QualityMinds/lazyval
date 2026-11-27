package com.qualityminds.lazyval.ksp.evaluation

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement

import java.util.stream.Stream

/**
 * Test-only generator for evaluation finding K13: it stands in for any processor or generator that emits a
 * {@code @LazyValue} type, which KSP only shows to the processors in the next round.
 * <p>
 * Emits {@code evaluation.k13.generated.RoundTwo} once, in the round where it is not yet among the elements, so a
 * processor that does handle later rounds does not see the file written twice.
 * <p>
 * Registered in the test resources' {@code META-INF/services}, and therefore loaded by every KSP run in this
 * module, but active only where a scenario includes the marker class {@code evaluation.k13.RoundTwoProbe}.
 */
class RoundTwoProbeGenerator implements Generator {

    static final String MARKER = 'evaluation.k13.RoundTwoProbe'
    static final String GENERATED_PACKAGE = 'evaluation.k13.generated'
    static final String GENERATED_TYPE = 'RoundTwo'

    @Override
    String generatorId() { 'round-two-probe' }

    @Override
    Set<String> requiredClasspath() { [MARKER] as Set }

    @Override
    Set<String> supportedOptions() { [] as Set }

    @Override
    Set<String> supersedes() { [] as Set }

    @Override
    Stream<GeneratorResult> generate(NonEmptySet<ValidatedKspGeneratorElement> elements, Generator.Context context) {
        def alreadySeen = elements.stream().anyMatch { ValidatedKspGeneratorElement element ->
            element.name.canonicalName() == "$GENERATED_PACKAGE.$GENERATED_TYPE".toString()
        }
        if (alreadySeen) {
            return Stream.empty()
        }
        def source = """package $GENERATED_PACKAGE

import com.qualityminds.lazyval.LazyValue

@LazyValue
data class $GENERATED_TYPE(val value: String)
"""
        Stream.of(new GeneratorResult.Kotlin(new GeneratorResult.Metadata(GENERATED_PACKAGE, GENERATED_TYPE), source)
                as GeneratorResult)
    }
}
