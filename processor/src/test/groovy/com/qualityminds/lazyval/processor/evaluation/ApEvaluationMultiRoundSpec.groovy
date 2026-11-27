package com.qualityminds.lazyval.processor.evaluation

import com.qualityminds.lazyval.LazyValue
import com.qualityminds.lazyval.processor.ApJacksonIT
import com.qualityminds.lazyval.processor.ApJpaIT
import com.qualityminds.lazyval.processor.internal.LazyvalProcessor
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * AI evaluation finding P10 (GitHub issue "Evaluation: processor", label evaluation): {@code LazyvalProcessor}
 * treats every round as the whole compilation. When another processor emits sources in round 1, Lazyval
 * regenerates its aggregate files in round 2 and javac aborts with an uncaught {@code FilerException}; and a
 * payload whose type only exists from round 2 on is generated against an error type instead of being deferred.
 * <p>
 * This specification intentionally fails on main; it documents the finding until it is fixed. Expected: the
 * compilation succeeds, with generated code for the domain-primitives of every round, and aggregates covering all
 * of them.
 * <p>
 * Runs javac in-process with explicit processors, because the Testkit cannot add a second processor.
 */
@Title("Evaluation P10 - multiple processing rounds")
class ApEvaluationMultiRoundSpec extends Specification {

    private static final String JPA_PACKAGE = "test/boundary/persistence/jpa"

    @TempDir
    Path workDir

    void "P10 a domain-primitive another processor generates in round 1 is processed without recreating aggregate files"() {
        given: 'a processor that emits a @LazyValue record in round 1'
        def roundOne = new RoundOneSourceGenerator("scenarios.evaluation.p10.GenCode", """\
                package scenarios.evaluation.p10;
                @com.qualityminds.lazyval.LazyValue
                public record GenCode(String value) {}
                """.stripIndent())

        when: 'Email is processed in round 1 and GenCode in round 2, by a per-type (JPA) and an aggregate (Jackson) generator'
        def errors = compile([new LazyvalProcessor(), roundOne],
                [ApJpaIT.dependencyJakartaPersistence, ApJacksonIT.dependencyJackson_2_Core, ApJacksonIT.dependencyJackson_2_Databind],
                "scenarios/evaluation/p10/Email.java")

        then: 'on main, javac throws: Attempt to recreate a file for type test.LazyvalJackson2Module'
        errors.isEmpty()

        and: 'both domain-primitives get their converter'
        Files.exists(workDir.resolve("generated/$JPA_PACKAGE/EmailAttributeConverter.java"))
        Files.exists(workDir.resolve("generated/$JPA_PACKAGE/GenCodeAttributeConverter.java"))

        and: 'the one Jackson module covers both'
        def module = Files.readString(workDir.resolve("generated/test/LazyvalJackson2Module.java"))
        module.contains("Email.class")
        module.contains("GenCode.class")
    }

    void "P10 a payload whose type another processor generates in round 1 is deferred until the type exists"() {
        given: 'a processor that emits the payload type in round 1'
        def roundOne = new RoundOneSourceGenerator("scenarios.evaluation.p10.GenStatus", """\
                package scenarios.evaluation.p10;
                public enum GenStatus { OPEN, CLOSED }
                """.stripIndent())

        when: 'Wrapper(GenStatus value) is compiled while GenStatus is still an error type in round 1'
        def errors = compile([new LazyvalProcessor(), roundOne], [ApJpaIT.dependencyJakartaPersistence],
                "scenarios/evaluation/p10/Wrapper.java")

        then: 'on main, the converter names GenStatus unqualified from another package: cannot find symbol'
        errors.isEmpty()

        and:
        Files.readString(workDir.resolve("generated/$JPA_PACKAGE/WrapperAttributeConverter.java"))
                .contains("scenarios.evaluation.p10.GenStatus")
    }

    /**
     * Compiles {@code sourceResources} into {@code workDir}, with Lazyval's core and {@code dependencies} on the
     * classpath and exactly {@code processors}; returns the error diagnostics.
     */
    private List<String> compile(List<Processor> processors, List<Dependency> dependencies, String... sourceResources) {
        def compiler = ToolProvider.systemJavaCompiler
        def diagnostics = new DiagnosticCollector<JavaFileObject>()
        def fileManager = compiler.getStandardFileManager(diagnostics, Locale.ENGLISH, StandardCharsets.UTF_8)
        try {
            def classpath = [new File(LazyValue.protectionDomain.codeSource.location.toURI())]
            dependencies.each { classpath.addAll(it.resolve().toSet()) }
            fileManager.setLocation(StandardLocation.CLASS_PATH, classpath)
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, [Files.createDirectories(workDir.resolve("classes")).toFile()])
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, [Files.createDirectories(workDir.resolve("generated")).toFile()])
            def sources = sourceResources.collect { new File(ApEvaluationMultiRoundSpec.classLoader.getResource(it).toURI()) }
            def task = compiler.getTask(null, fileManager, diagnostics,
                    ["-Alazyval.generators.basePackage=test"], null, fileManager.getJavaFileObjects(*sources))
            task.setProcessors(processors)
            task.call()
            return diagnostics.diagnostics
                    .findAll { it.kind == Diagnostic.Kind.ERROR }
                    .collect { it.getMessage(Locale.ENGLISH) }
        } finally {
            fileManager.close()
        }
    }

    /**
     * Stand-in for any processor that emits sources in round 1 — record builders, Immutables, protobuf-lite and
     * the like: writes one source file in the first round and nothing afterwards.
     */
    static class RoundOneSourceGenerator extends AbstractProcessor {

        private final String qualifiedName
        private final String source
        private boolean written

        RoundOneSourceGenerator(String qualifiedName, String source) {
            this.qualifiedName = qualifiedName
            this.source = source
        }

        @Override
        Set<String> getSupportedAnnotationTypes() {
            return ["*"] as Set
        }

        @Override
        SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported()
        }

        @Override
        boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (!written) {
                written = true
                processingEnv.filer.createSourceFile(qualifiedName).openWriter().withCloseable { it.write(source) }
            }
            return false
        }
    }
}
