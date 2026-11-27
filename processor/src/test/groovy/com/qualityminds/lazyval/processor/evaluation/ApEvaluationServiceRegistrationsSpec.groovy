package com.qualityminds.lazyval.processor.evaluation

import com.qualityminds.lazyval.LazyValue
import com.qualityminds.lazyval.processor.ApBeanValidationIT
import com.qualityminds.lazyval.processor.internal.LazyvalProcessor
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title

import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * AI evaluation finding P7 (GitHub issue "Evaluation: processor", label evaluation): {@code LazyvalProcessor} writes
 * {@code META-INF/services/<spi>} through {@code Filer.createResource}, which replaces a file of that name that
 * is already in CLASS_OUTPUT. Maven's process-resources phase copies a project's own
 * {@code src/main/resources/META-INF/services} there before compiling, so the application's registrations are
 * lost from the built artifact.
 * <p>
 * This specification intentionally fails on main; it documents the finding until it is fixed. Expected: the
 * provider lines already in the file survive, and the generated providers are added to them.
 * <p>
 * Runs javac in-process with {@link LazyvalProcessor} set explicitly, because the Testkit cleans the project
 * directory and cannot pre-seed CLASS_OUTPUT.
 */
@Title("Evaluation P7 - existing service registrations")
class ApEvaluationServiceRegistrationsSpec extends Specification {

    private static final String VALUE_EXTRACTOR_SPI = "jakarta.validation.valueextraction.ValueExtractor"
    private static final String APPLICATION_PROVIDER = "com.acme.validation.MoneyValueExtractor"

    @TempDir
    Path workDir

    void "P7 provider lines already in CLASS_OUTPUT survive the generated service registration"() {
        given: "process-resources has copied the application's own ValueExtractor registration into CLASS_OUTPUT"
        def classOutput = Files.createDirectories(workDir.resolve("classes"))
        def servicesFile = Files.createDirectories(classOutput.resolve("META-INF/services")).resolve(VALUE_EXTRACTOR_SPI)
        Files.writeString(servicesFile, APPLICATION_PROVIDER + "\n")

        when: 'a domain-primitive is compiled with Bean Validation on the classpath'
        def errors = compile(classOutput, Files.createDirectories(workDir.resolve("generated")),
                "scenarios/evaluation/p7/Email.java")

        then: 'the generated ValueExtractor is registered'
        errors.isEmpty()
        def providers = Files.readAllLines(servicesFile)*.trim().findAll { !it.isEmpty() && !it.startsWith("#") }
        providers.contains("test.domain.EmailValueExtractor")

        and: "the application's own registration is still there"
        providers.contains(APPLICATION_PROVIDER)
    }

    /** Compiles {@code sourceResources} with only Lazyval as processor; returns the error diagnostics. */
    private static List<String> compile(Path classOutput, Path sourceOutput, String... sourceResources) {
        def compiler = ToolProvider.systemJavaCompiler
        def diagnostics = new DiagnosticCollector<JavaFileObject>()
        def fileManager = compiler.getStandardFileManager(diagnostics, Locale.ENGLISH, StandardCharsets.UTF_8)
        try {
            def classpath = [new File(LazyValue.protectionDomain.codeSource.location.toURI())]
            classpath.addAll(ApBeanValidationIT.dependencyBeanValidation.resolve().toSet())
            fileManager.setLocation(StandardLocation.CLASS_PATH, classpath)
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, [classOutput.toFile()])
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, [sourceOutput.toFile()])
            def sources = sourceResources.collect { new File(ApEvaluationServiceRegistrationsSpec.classLoader.getResource(it).toURI()) }
            def task = compiler.getTask(null, fileManager, diagnostics,
                    ["-Alazyval.generators.basePackage=test"], null, fileManager.getJavaFileObjects(*sources))
            task.setProcessors([new LazyvalProcessor()])
            task.call()
            return diagnostics.diagnostics
                    .findAll { it.kind == Diagnostic.Kind.ERROR }
                    .collect { it.getMessage(Locale.ENGLISH) }
        } finally {
            fileManager.close()
        }
    }
}
