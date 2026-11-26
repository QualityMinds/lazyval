package com.qualityminds.lazyval.testkit.internal.toolchain

import spock.lang.Specification
import spock.lang.TempDir

import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import java.nio.file.Files
import java.nio.file.Path

/**
 * Pins {@link JavaLibrary} on its own: what it compiles, against what, and that no annotation processor
 * takes part. The sources are written into the temp dir rather than kept as resources, since each feature
 * needs its own small library and none of them is a scenario.
 */
class JavaLibrarySpec extends Specification {

    @TempDir
    Path tempDir

    void "A scenario without a library adds nothing to the classpath"() {
        given:
        def outputDir = tempDir.resolve('library')

        expect:
        JavaLibrary.compile([], [], outputDir).isEmpty()

        and: 'not even an empty output directory'
        !Files.exists(outputDir)
    }

    void "The library's classes land in the output directory, which is what joins the classpath"() {
        given:
        def source = write('src/shared/Distance.java', '''
                package shared;
                public record Distance(int meters) { }
                ''')
        def outputDir = tempDir.resolve('library')

        when:
        def classpath = JavaLibrary.compile([source], [], outputDir)

        then:
        classpath == [outputDir.toFile()]
        Files.exists(outputDir.resolve('shared/Distance.class'))
    }

    void "The library compiles against the classpath it is given"() {
        given: 'a library depending on a type compiled before it'
        def upstream = JavaLibrary.compile([write('src/upstream/Meters.java', '''
                package upstream;
                public record Meters(int value) { }
                ''')], [], tempDir.resolve('upstream'))
        def source = write('src/shared/Distance.java', '''
                package shared;
                public record Distance(upstream.Meters meters) { }
                ''')

        when:
        def classpath = JavaLibrary.compile([source], upstream, tempDir.resolve('library'))

        then:
        Files.exists(classpath.first().toPath().resolve('shared/Distance.class'))
    }

    void "A library that does not compile fails the run, naming the error"() {
        given:
        def source = write('src/shared/Distance.java', '''
                package shared;
                public record Distance(Meters meters) { }
                ''')

        when:
        JavaLibrary.compile([source], [], tempDir.resolve('library'))

        then:
        def e = thrown(IllegalStateException)
        e.message.startsWith("The scenario's library does not compile: ")
        e.message.contains('Meters')
    }

    void "No annotation processor on the classpath runs while the library compiles"() {
        given: 'a processor that fails every compilation it takes part in, discoverable through META-INF/services'
        def trap = processorTrap()
        def source = write('src/shared/Distance.java', '''
                package shared;
                public record Distance(int meters) { }
                ''')

        expect: 'the trap is armed: plain javac with processing switched on discovers it and fails'
        def control = compileWithProcessing(source, trap)
        !control.compiled
        control.errors.contains('annotation processing ran')

        when:
        def classpath = JavaLibrary.compile([source], trap, tempDir.resolve('library'))

        then:
        notThrown(IllegalStateException)
        Files.exists(classpath.first().toPath().resolve('shared/Distance.class'))
    }

    /** A processor reporting an error in every round, registered as a service next to its class. */
    private List<File> processorTrap() {
        def processor = write('trap-src/trap/Trap.java', '''
                package trap;

                import java.util.Set;
                import javax.annotation.processing.*;
                import javax.lang.model.SourceVersion;
                import javax.lang.model.element.TypeElement;
                import javax.tools.Diagnostic;

                @SupportedAnnotationTypes("*")
                public class Trap extends AbstractProcessor {
                    @Override
                    public SourceVersion getSupportedSourceVersion() {
                        return SourceVersion.latestSupported();
                    }

                    @Override
                    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "annotation processing ran");
                        return false;
                    }
                }
                ''')
        def trapDir = tempDir.resolve('trap')
        def classpath = JavaLibrary.compile([processor], [], trapDir)
        def services = trapDir.resolve('META-INF/services/javax.annotation.processing.Processor')
        Files.createDirectories(services.parent)
        Files.writeString(services, 'trap.Trap\n')
        classpath
    }

    /**
     * Plain javac over the same source and classpath, with {@code -proc:full}: since JDK 23 javac no longer
     * runs processors it merely discovers, so the control has to ask for them.
     */
    private Map compileWithProcessing(File source, List<File> classpath) {
        def compiler = ToolProvider.systemJavaCompiler
        def diagnostics = new DiagnosticCollector<JavaFileObject>()
        def fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)
        try {
            def outputDir = Files.createDirectories(tempDir.resolve('control')).toFile()
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, [outputDir])
            fileManager.setLocation(StandardLocation.CLASS_PATH, classpath)
            def compiled = compiler.getTask(null, fileManager, diagnostics, ['-proc:full'], null,
                    fileManager.getJavaFileObjectsFromFiles([source])).call()
            [compiled: compiled, errors: diagnostics.diagnostics*.getMessage(Locale.ROOT).join('; ')]
        } finally {
            fileManager.close()
        }
    }

    private File write(String relativePath, String content) {
        def file = tempDir.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content.stripIndent())
        file.toFile()
    }
}
