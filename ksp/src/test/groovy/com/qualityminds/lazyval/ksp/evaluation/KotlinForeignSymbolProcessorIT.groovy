package com.qualityminds.lazyval.ksp.evaluation

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import kotlin.KotlinVersion
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
 * AI evaluation finding T3, Kotlin twin: the KSP step runs every {@code SymbolProcessorProvider} that
 * {@code ServiceLoader} finds through a loader over the scenario's dependencies, with the test classpath
 * as its parent ({@code KspStep}). A KSP processor shipped in a dependency jar or sitting on the test
 * classpath (Room, Moshi codegen, Koin, ...) runs inside the scenario and leaks its output and
 * diagnostics into the {@code Testresult}.
 * <p>
 * This test intentionally fails on {@code main}. The dependency is a jar built at test time in a
 * {@code @TempDir} and seeded into the {@link Dependency} cache, so nothing is resolved from a repo.
 * Its processor logs a warning and generates one Kotlin file, which makes it visible in the result.
 */
class KotlinForeignSymbolProcessorIT extends Specification {

    private static final String PROVIDER_SOURCE = '''\
package evaluation.foreign;

import com.google.devtools.ksp.processing.Dependencies;
import com.google.devtools.ksp.processing.Resolver;
import com.google.devtools.ksp.processing.SymbolProcessor;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
import com.google.devtools.ksp.processing.SymbolProcessorProvider;
import com.google.devtools.ksp.symbol.KSAnnotated;
import com.google.devtools.ksp.symbol.KSFile;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ForeignSymbolProcessorProvider implements SymbolProcessorProvider {
    @Override
    public SymbolProcessor create(SymbolProcessorEnvironment environment) {
        return new SymbolProcessor() {
            private boolean done;

            @Override
            public List<KSAnnotated> process(Resolver resolver) {
                if (!done) {
                    done = true;
                    environment.getLogger().warn("foreign symbol processor ran in the scenario", null);
                    try (OutputStream out = environment.getCodeGenerator().createNewFile(
                            new Dependencies(false, new KSFile[0]), "evaluation.foreign", "ForeignProcessorWasHere", "kt")) {
                        out.write("package evaluation.foreign\\n\\nclass ForeignProcessorWasHere\\n".getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                return List.of();
            }
        };
    }
}
'''

    @TempDir
    Path projectDir

    @TempDir
    Path libraryDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    void "T3 a KSP processor inside a scenario dependency does not run in the scenario"() {
        given: 'a library jar that happens to ship a KSP processor'
        def library = new Dependency('evaluation.t3', 'library-with-symbol-processor', '1.0')
        def jar = buildJarWithSymbolProcessor(libraryDir)
        library.resolve { NonEmptySet.of(jar.toFile()) }

        when:
        def result = testkitKotlin.run(projectDir, Scenario.Kotlin.isbn().withDependencies(library))

        then: 'only Lazyval processes the scenario, and without a generator dependency it generates nothing'
        result == new Testresult.Kotlin.NothingGenerated()
    }

    private static Path buildJarWithSymbolProcessor(Path workDir) {
        def source = Files.createDirectories(workDir.resolve('src')).resolve('ForeignSymbolProcessorProvider.java')
        Files.writeString(source, PROVIDER_SOURCE)
        def classes = Files.createDirectories(workDir.resolve('classes'))
        def classpath = [SymbolProcessorProvider, KotlinVersion]
                .collect { new File(it.protectionDomain.codeSource.location.toURI()).path }
                .join(File.pathSeparator)
        def compiler = ToolProvider.systemJavaCompiler
        compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8).withCloseable { fileManager ->
            def compiled = compiler.getTask(null, fileManager, null,
                    ['-proc:none', '-classpath', classpath, '-d', classes.toString()], null,
                    fileManager.getJavaFileObjects(source.toFile())).call()
            assert compiled
        }
        def jar = workDir.resolve('library-with-symbol-processor-1.0.jar')
        new JarOutputStream(Files.newOutputStream(jar)).withCloseable { out ->
            out.putNextEntry(new JarEntry('META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider'))
            out.write('evaluation.foreign.ForeignSymbolProcessorProvider\n'.getBytes(StandardCharsets.UTF_8))
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
