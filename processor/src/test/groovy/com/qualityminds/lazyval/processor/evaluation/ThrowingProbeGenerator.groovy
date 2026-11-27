package com.qualityminds.lazyval.processor.evaluation

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.processor.spi.Generator
import com.qualityminds.lazyval.processor.spi.GeneratorResult
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement

import java.util.stream.Stream

/**
 * Test-only generator that crashes the way the most common SPI-author bug does: an exception out of
 * {@code generate}.
 * <p>
 * Registered in the test resources' {@code META-INF/services}, and therefore loaded by every javac run in
 * this module, but active only where a scenario includes the marker class {@code scenarios.evaluation.ThrowingProbe}.
 * Used by {@link GeneratorExceptionContractIT} (AI evaluation finding T7).
 */
class ThrowingProbeGenerator implements Generator {

    static final String MARKER = 'scenarios.evaluation.ThrowingProbe'
    static final String MESSAGE = 'simulated generator bug'

    @Override
    String generatorId() { 'evaluation-throwing-probe' }

    @Override
    Set<String> requiredClasspath() { [MARKER] as Set }

    @Override
    Set<String> supportedOptions() { [] as Set }

    @Override
    Stream<GeneratorResult> generate(NonEmptySet<ValidatedGeneratorElement> elements, Generator.Context context) {
        throw new NullPointerException(MESSAGE)
    }
}
