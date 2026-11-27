package com.qualityminds.lazyval.processor.evaluation

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import javax.tools.ToolProvider
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/**
 * AI evaluation finding T3: the Java toolchain hands javac every {@code javax.annotation.processing.Processor}
 * that {@code ServiceLoader} finds through a loader over the scenario's dependencies, with the test
 * classpath as its parent ({@code JavaToolchain}). Any processor shipped in a dependency jar (Lombok,
 * AutoService, MapStruct's processor, log4j's PluginProcessor, ...) or sitting on the test classpath runs
 * in-process inside the scenario and leaks its output and diagnostics into the {@code Testresult}.
 * <p>
 * This test intentionally fails on {@code main}. The dependency is a jar built at test time in a
 * {@code @TempDir} and seeded into the {@link Dependency} cache, so nothing is resolved from a repo.
 * Its processor reports a warning and generates one source file, which makes it visible in the result.
 */
class ForeignAnnotationProcessorIT extends Specification {

    private static final String PROCESSOR_SOURCE = '''\
package evaluation.foreign;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes("*")
public class ForeignProcessor extends AbstractProcessor {
    private boolean done;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!done) {
            done = true;
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "foreign processor ran in the scenario");
            try (Writer writer = processingEnv.getFiler().createSourceFile("evaluation.foreign.ForeignProcessorWasHere").openWriter()) {
                writer.write("package evaluation.foreign; public class ForeignProcessorWasHere {}");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return false;
    }
}
'''

    @TempDir
    Path projectDir

    @TempDir
    Path libraryDir

    @Shared
    def testkitJava = Testkit.java()

    void "T3 an annotation processor inside a scenario dependency does not run in the scenario"() {
        given: 'a library jar that happens to ship an annotation processor'
        def library = new Dependency('evaluation.t3', 'library-with-processor', '1.0')
        def jar = buildJarWithProcessor(libraryDir)
        library.resolve { NonEmptySet.of(jar.toFile()) }

        when:
        def result = testkitJava.run(projectDir, Scenario.Java.isbn().withDependencies(library))

        then: 'only Lazyval processes the scenario, and without a generator dependency it generates nothing'
        result == new Testresult.Java.NothingGenerated()
    }

    private static Path buildJarWithProcessor(Path workDir) {
        def source = Files.createDirectories(workDir.resolve('src')).resolve('ForeignProcessor.java')
        Files.writeString(source, PROCESSOR_SOURCE)
        def classes = Files.createDirectories(workDir.resolve('classes'))
        def compiler = ToolProvider.systemJavaCompiler
        compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8).withCloseable { fileManager ->
            def compiled = compiler.getTask(null, fileManager, null,
                    ['-proc:none', '-d', classes.toString()], null,
                    fileManager.getJavaFileObjects(source.toFile())).call()
            assert compiled
        }
        def jar = workDir.resolve('library-with-processor-1.0.jar')
        new JarOutputStream(Files.newOutputStream(jar)).withCloseable { out ->
            out.putNextEntry(new JarEntry('META-INF/services/javax.annotation.processing.Processor'))
            out.write('evaluation.foreign.ForeignProcessor\n'.getBytes(StandardCharsets.UTF_8))
            out.closeEntry()
            Files.walk(classes).withCloseable { paths ->
                paths.filter { Files.isRegularFile(it) }.forEach { Path file ->
                    out.putNextEntry(new JarEntry(classes.relativize(file).toString().replace(File.separator, '/')))
                    out.write(Files.readAllBytes(file))
                    out.closeEntry()
                }
            }
        }
        jar
    }
}
