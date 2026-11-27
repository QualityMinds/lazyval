package com.qualityminds.lazyval.ksp.evaluation

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import com.qualityminds.lazyval.LazyValue
import com.qualityminds.lazyval.ksp.internal.LazyvalSymbolProcessorProvider
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import kotlin.KotlinVersion
import kotlin.Unit
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title

import java.nio.file.Files
import java.nio.file.Path

/**
 * AI evaluation finding K2 (GitHub issue "Evaluation: ksp", label evaluation). This specification intentionally
 * fails on main: it documents the expected behaviour, not the current one.
 * <p>
 * Drives KSP2 directly with {@code incremental = true}, because the Testkit always runs a clean, non-incremental
 * build. Both runs share the project, output and caches directories, as two builds of one module do.
 * <p>
 * Every generated file lists the sources of the module's own domain-primitives in its {@code Dependencies}, but
 * not the file holding {@code @LazyvalConfiguration}. After an edit to an unrelated file KSP hands the processor
 * the dirty files only; the aggregating outputs pull the domain-primitives' sources back in, the configuration
 * holder stays out, so {@code externalTypes} reads as empty and the regenerated JSON-B adapters lose
 * {@code java.time.Year}. A module with only external types is not affected; one with local types next to them is.
 */
@Title("Evaluation K2 - incremental KSP and externalTypes")
class K2IncrementalExternalTypesIT extends Specification {

    static final Dependency JSONB_API = new Dependency("jakarta.json.bind", "jakarta.json.bind-api", "3.0.1")
    static final List<String> SOURCES = ['Config.kt', 'Email.kt', 'Service.kt']

    @TempDir
    Path projectDir

    void "K2 an incremental run after editing an unrelated file keeps the adapter for an external type"() {
        given: 'a module with a local domain-primitive, java.time.Year as external type and an unrelated class'
        def sourceDir = Files.createDirectories(projectDir.resolve('src'))
        SOURCES.each { name ->
            Files.writeString(sourceDir.resolve(name),
                    getClass().classLoader.getResource("scenarios/evaluation/k2/$name").getText('UTF-8'))
        }
        def service = sourceDir.resolve('Service.kt')

        when: 'the first build processes every file'
        def firstRun = runKsp(sourceDir, [])

        then: 'precondition: both domain-primitives are adapted'
        firstRun.exitCode == KotlinSymbolProcessing.ExitCode.OK
        firstRun.errors.empty
        adapters().contains('EmailAdapter')
        adapters().contains('YearAdapter')

        when: 'only the unrelated class is edited, and KSP is told so'
        Files.writeString(service, Files.readString(service) + '\n// edited\n')
        def secondRun = runKsp(sourceDir, [service.toFile()])

        then: 'the regenerated adapters still cover the external type'
        secondRun.exitCode == KotlinSymbolProcessing.ExitCode.OK
        secondRun.errors.empty
        adapters().contains('EmailAdapter')
        adapters().contains('YearAdapter')
    }

    private String adapters() {
        Files.readString(projectDir.resolve('build/generated/ksp/kotlin/test/LazyvalJsonbAdapters.kt'))
    }

    private Run runKsp(Path sourceDir, List<File> modified) {
        def build = projectDir.resolve('build')
        def kotlin = KotlinVersion.CURRENT
        def builder = new KSPJvmConfig.Builder()
        builder.jvmTarget = '17'
        builder.languageVersion = "${kotlin.major}.${kotlin.minor}".toString()
        builder.apiVersion = "${kotlin.major}.${kotlin.minor}".toString()
        builder.moduleName = 'test'
        builder.jdkHome = Path.of(System.getProperty('java.home')).toFile()
        builder.projectBaseDir = projectDir.toFile()
        builder.outputBaseDir = Files.createDirectories(build).toFile()
        builder.classOutputDir = Files.createDirectories(build.resolve('classes')).toFile()
        builder.javaOutputDir = Files.createDirectories(build.resolve('generated/ksp/java')).toFile()
        builder.kotlinOutputDir = Files.createDirectories(build.resolve('generated/ksp/kotlin')).toFile()
        builder.resourceOutputDir = Files.createDirectories(build.resolve('generated/ksp/resources')).toFile()
        builder.cachesDir = Files.createDirectories(build.resolve('kspCaches')).toFile()
        builder.sourceRoots = [sourceDir.toFile()]
        builder.libraries = libraries()
        builder.processorOptions = ['lazyval.generators.basePackage': 'test']
        builder.incremental = true
        builder.modifiedSources = modified
        builder.removedSources = []
        builder.changedClasses = []

        def logger = new CollectingLogger()
        def exitCode = new KotlinSymbolProcessing(builder.build(), [new LazyvalSymbolProcessorProvider()], logger).execute()
        new Run(exitCode, logger.errors)
    }

    private static List<File> libraries() {
        // the Lazyval annotations and the Kotlin standard library, as the build would put them on KSP's classpath
        [LazyValue, Unit].collect { new File(it.protectionDomain.codeSource.location.toURI()) } +
                JSONB_API.resolve().toSet().toList()
    }

    private static class Run {
        final KotlinSymbolProcessing.ExitCode exitCode
        final List<String> errors

        Run(KotlinSymbolProcessing.ExitCode exitCode, List<String> errors) {
            this.exitCode = exitCode
            this.errors = errors
        }
    }

    private static class CollectingLogger implements KSPLogger {
        final List<String> errors = []

        @Override
        void logging(String message, KSNode symbol) {}

        @Override
        void info(String message, KSNode symbol) {}

        @Override
        void warn(String message, KSNode symbol) {}

        @Override
        void error(String message, KSNode symbol) {
            errors << message
        }

        @Override
        void exception(Throwable e) {
            errors << e.toString()
        }
    }
}
