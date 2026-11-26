package com.qualityminds.lazyval.ksp

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement

import java.util.stream.Stream

/**
 * Test-only generator that rebuilds every domain-primitive in generated Java from a boxed payload, through
 * {@code element.java.createFromBoxed}. No stock generator does that on the KSP side, so without this the
 * expression is only ever checked as a string, never against the declarations javac resolves it to.
 * <p>
 * Registered in the test resources' {@code META-INF/services}, and therefore loaded by every KSP run in this
 * module, but active only where a scenario includes the marker class {@code scenarios.edge.CreateFromBoxedProbe}.
 */
class CreateFromBoxedProbeGenerator implements Generator {

    static final String MARKER = 'scenarios.edge.CreateFromBoxedProbe'

    @Override
    String generatorId() { 'create-from-boxed-probe' }

    @Override
    Set<String> requiredClasspath() { [MARKER] as Set }

    @Override
    Set<String> supportedOptions() { [] as Set }

    @Override
    Set<String> supersedes() { [] as Set }

    @Override
    Stream<GeneratorResult> generate(NonEmptySet<ValidatedKspGeneratorElement> elements, Generator.Context context) {
        elements.stream().map { ValidatedKspGeneratorElement element ->
            def className = element.name.flatName() + 'Probe'
            def source = """package probe;

public final class $className {

    public static ${element.name.canonicalName()} rebuild(${element.payload.boxed().canonicalName()} boxed) {
        return ${element.java.createFromBoxed('boxed').asSource()};
    }
}
"""
            new GeneratorResult.Java(new GeneratorResult.Metadata('probe', className), source) as GeneratorResult
        }
    }
}
