package com.qualityminds.lazyval.processor.evaluation

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.processor.spi.Generator
import com.qualityminds.lazyval.processor.spi.GeneratorResult
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement

import java.util.stream.Stream

/**
 * Test-only generator with an innocent-looking instance field, the way an SPI author might keep a cache
 * or a "warned once" flag. It names the class it generates after the number of times it has been called,
 * so any state surviving from one Testkit run to the next shows up in the result.
 * <p>
 * Registered in the test resources' {@code META-INF/services}, and therefore loaded by every javac run in
 * this module, but active only where a scenario includes the marker class {@code scenarios.evaluation.StatefulProbe}.
 * Used by {@link GeneratorStateIT} (AI evaluation finding T6).
 */
class StatefulProbeGenerator implements Generator {

    static final String MARKER = 'scenarios.evaluation.StatefulProbe'

    private int invocations

    @Override
    String generatorId() { 'evaluation-stateful-probe' }

    @Override
    Set<String> requiredClasspath() { [MARKER] as Set }

    @Override
    Set<String> supportedOptions() { [] as Set }

    @Override
    Stream<GeneratorResult> generate(NonEmptySet<ValidatedGeneratorElement> elements, Generator.Context context) {
        invocations++
        def className = "Run${invocations}".toString()
        def source = "package evaluation.generated;\n\npublic final class ${className} {}\n".toString()
        Stream.of(new GeneratorResult.Java(new GeneratorResult.Metadata('evaluation.generated', className), source) as GeneratorResult)
    }
}
