package com.qualityminds.lazyval.ksp.evaluation

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement

import java.util.stream.Stream

/**
 * Test-only generator that crashes the way the most common SPI-author bug does: an exception out of
 * {@code generate}.
 * <p>
 * Registered in the test resources' {@code META-INF/services}, and therefore loaded by every KSP run in this
 * module, but active only where a scenario includes the marker class {@code scenarios.evaluation.ThrowingProbe}.
 * Used by {@link KotlinGeneratorExceptionContractIT} (AI evaluation finding T7).
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
    Set<String> supersedes() { [] as Set }

    @Override
    Stream<GeneratorResult> generate(NonEmptySet<ValidatedKspGeneratorElement> elements, Generator.Context context) {
        throw new NullPointerException(MESSAGE)
    }
}
