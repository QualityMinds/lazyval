package com.qualityminds.lazyval.processor

import spock.lang.Specification
import spock.lang.Title

import javax.tools.ToolProvider
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile

/**
 * Guards the SPI's <em>published</em> surface, which nothing else in the build touches: every other IT
 * compiles against {@code target/classes}, where no shading has happened yet.
 *
 * {@code DotName} and {@code NonEmptySet} live in lazyval-utils, which is deliberately never deployed.
 * They reach a generator author only because maven-shade-plugin inlines them into this jar
 * <em>without</em> relocating them, and because {@code minimizeJar} retains a bundled class while
 * something still references it. Both are properties of the build rather than of the source, so
 * breaking either would still compile here and fail only in a consumer's project — which is what this
 * test exists to prevent.
 */
@Title("Shaded artifact")
class ShadedArtifactIT extends Specification {

    void "the published jar carries the SPI's shared types at their original names"() {
        expect: "minimizeJar kept them, because both sit in Generator's own signatures"
        jarEntries().containsAll([
                "com/qualityminds/lazyval/naming/DotName.class",
                "com/qualityminds/lazyval/naming/Payload.class",
                "com/qualityminds/lazyval/collections/NonEmptySet.class"])
    }

    void "no Lazyval-owned package is relocated"() {
        expect: "the relocation list names third-party packages only, so these names stay reachable"
        relocatedLazyvalPackages().isEmpty()
    }

    void "an external generator compiles against the published jar alone"() {
        given: "a third-party generator, with nothing but the shaded artifact to compile against"
        def dir = Files.createTempDirectory("lazyval-probe")
        def probe = dir.resolve("Probe.java")
        Files.writeString(probe, PROBE_SOURCE)
        def diagnostics = new ByteArrayOutputStream()

        when: "javac sees exactly what a consumer's processorpath would offer"
        def status = ToolProvider.systemJavaCompiler.run(
                null, null, diagnostics,
                "-classpath", shadedJar().toString(),
                "-d", dir.toString(),
                probe.toString())

        then: "it resolves DotName, NonEmptySet and the boxing table out of that one jar"
        assert status == 0, "probe generator did not compile against the shaded jar:\n$diagnostics"
    }

    // ---- helpers ----------------------------------------------------------------------------
    // Deliberately local to this spec rather than shared with the KSP module's copy: the only
    // homes for shared test code are lazyval-utils (not on the test classpath of both) and the
    // published testkit, and 20 lines of test helper is not worth widening a shipped artifact for.

    /**
     * The shaded artifact this module publishes — not shade's {@code original-} pre-shade copy.
     *
     * Located relative to the compiled test classes rather than from {@code basedir}, which failsafe
     * points at the build directory rather than the module root.
     */
    private static Path shadedJar() {
        def testClasses = new File(ShadedArtifactIT.protectionDomain.codeSource.location.toURI())
        def target = testClasses.parentFile
        def jars = target.listFiles({ File f ->
            f.name.endsWith(".jar") && !f.name.startsWith("original-") &&
                    !f.name.contains("-sources") && !f.name.contains("-javadoc")
        } as FileFilter)
        assert jars != null && jars.length == 1,
                "expected exactly one shaded jar in $target, found ${jars*.name}"
        jars[0].toPath()
    }

    private static List<String> jarEntries() {
        new JarFile(shadedJar().toFile()).withCloseable { jar -> jar.entries().collect { it.name } }
    }

    /** Lazyval's own packages found under the {@code shaded/} prefix — always expected to be empty. */
    private static List<String> relocatedLazyvalPackages() {
        jarEntries().findAll { it.startsWith("com/qualityminds/lazyval/shaded/") }
                .findAll { it.contains("/naming/") || it.contains("/collections/") }
    }

    private static final String PROBE_SOURCE = '''
package probe;

import com.qualityminds.lazyval.collections.NonEmptySet;
import com.qualityminds.lazyval.naming.DotName;
import com.qualityminds.lazyval.naming.Payload;
import com.qualityminds.lazyval.processor.spi.Generator;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import java.util.Set;
import java.util.stream.Stream;

/** Stands in for a third-party generator: nothing here may need more than the published jar. */
public final class Probe implements Generator {

    @Override public String generatorId() { return "probe"; }
    @Override public Set<String> requiredClasspath() { return Set.of(); }
    @Override public Set<String> supportedOptions() { return Set.of(); }

    @Override
    public Stream<GeneratorResult> generate(NonEmptySet<ValidatedGeneratorElement> elements, Context ctx) {
        return elements.stream().map(element -> {
            DotName name = element.name();
            Payload payload = element.payload();
            String slot = element.isPayloadPrimitive()
                    ? payload.boxed().canonicalName()
                    : payload.identifier();
            String body = slot + " v = " + element.java().read("source") + "; return "
                    + element.java().create("v").asSource() + ";";
            return new GeneratorResult.Java(
                    new GeneratorResult.Metadata(name.packageName(), name.flatName() + "Probe"),
                    body);
        });
    }
}
'''
}
