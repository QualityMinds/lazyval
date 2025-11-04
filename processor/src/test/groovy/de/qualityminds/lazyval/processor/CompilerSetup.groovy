package de.qualityminds.lazyval.processor

import de.qualityminds.lazyval.test.MavenResolver
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.tools.Diagnostic
import javax.tools.DiagnosticListener
import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import java.nio.file.Files
import java.nio.file.Path

/**
 * A simple DiagnosticListener which behaves the same as javax.tools.DiagnosticCollector, but also logs to SLF4J
 * for better tracing during testing.
 */
class LoggingDiagnosticsCollector<S> implements DiagnosticListener<S> {

    private static Logger logger = LoggerFactory.getLogger(LoggingDiagnosticsCollector.class)
    private List<Diagnostic<? extends S>> diagnostics =
            Collections.synchronizedList(new ArrayList<Diagnostic<? extends S>>())

    @Override
    void report(Diagnostic<? extends S> diagnostic) {
        Objects.requireNonNull(diagnostic);
        diagnostics.add(diagnostic)
        switch(diagnostic.getKind()){
            case Diagnostic.Kind.ERROR:
                logger.error(diagnostic.getMessage(Locale.ENGLISH))
                break
            case Diagnostic.Kind.WARNING:
            case Diagnostic.Kind.MANDATORY_WARNING:
                logger.warn(diagnostic.getMessage(Locale.ENGLISH))
                break
            case Diagnostic.Kind.NOTE:
                logger.info(diagnostic.getMessage(Locale.ENGLISH))
                break
            case Diagnostic.Kind.OTHER:
                logger.debug(diagnostic.getMessage(Locale.ENGLISH))
                break
            default:
                throw new Exception("unknown enum value ${diagnostic.getKind()}")
        }
    }

    List<Diagnostic<? extends S>> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics)
    }
}


class CompilerSetup {

    private static final Logger logger = LoggerFactory.getLogger(CompilerSetup.class)

    // following dependencies are dynamically added to the compilation-task during test execution
    private static final String DEP_MAPSTRUCT = "org.mapstruct:mapstruct:1.6.3"
    private static final String DEP_MAPSTRUCT_PROCESSOR = "org.mapstruct:mapstruct-processor:1.6.3"
    private static final String DEP_JPA = "jakarta.persistence:jakarta.persistence-api:3.2.0"

    enum Libraries {
        NONE,
        ALL,
        MAPSTRUCT,
        JPA
    }

    private final JavaCompiler.CompilationTask task
    private final LoggingDiagnosticsCollector diagnostics
    private final Path sourceOutputDir

    private CompilerSetup(JavaCompiler.CompilationTask task, LoggingDiagnosticsCollector diagnostics, Path sourceOutputDir){
        this.task = task
        this.diagnostics = diagnostics
        this.sourceOutputDir = sourceOutputDir
    }

    CompilerResult run() {
        boolean result = task.call()
        new CompilerResult(
                result,
                diagnostics.getDiagnostics(),
                new TreeSet<>(Files.walk(sourceOutputDir).filter{ p -> !Files.isDirectory(p) }.toList())
        )
    }


    static CompilerSetup setupTask(ClassLoader classloader, String fileToCompile, Path tempDir, Libraries libraries, List<String> disabledGenerators) {
        var compiler = ToolProvider.getSystemJavaCompiler()
        var diagnostics = new LoggingDiagnosticsCollector<JavaFileObject>()

        var isMapstructDependencyAvailable = libraries == Libraries.ALL || libraries == Libraries.MAPSTRUCT
        var isJpaDependencyAvailable = libraries == Libraries.ALL || libraries == Libraries.JPA

        List<File> additionalClasspath = new ArrayList<>()
        additionalClasspath.addAll(MavenResolver.getCoreModuleClasses())

        if (isMapstructDependencyAvailable) {
            additionalClasspath.addAll(MavenResolver.resolveDependencies(DEP_MAPSTRUCT, DEP_MAPSTRUCT_PROCESSOR))
        }
        if (isJpaDependencyAvailable) {
            additionalClasspath.addAll(MavenResolver.resolveDependencies(DEP_JPA))
        }

        var fileManager = compiler.getStandardFileManager(diagnostics, null, null)
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(tempDir.toFile()))
        fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, Arrays.asList(tempDir.toFile()))

        if (!additionalClasspath.isEmpty()) {
            fileManager.setLocation(StandardLocation.CLASS_PATH, additionalClasspath)
        }

        var idGeneratorFile = loadFileResource(classloader, "util/IdGenerator.java")
        var file = loadFileResource(classloader, "test/$fileToCompile")


        var compilationUnits = fileManager.getJavaFileObjects(idGeneratorFile, file)
        List<String> options = null
        if (!disabledGenerators.isEmpty()) {
            options = List.of("-Alazyval.disabledGenerators=" + disabledGenerators.join(","))
        }
        var task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits)
        task.setProcessors(Collections.singletonList(new LazyvalProcessor()))

        new CompilerSetup(task, diagnostics, fileManager.getLocation(StandardLocation.SOURCE_OUTPUT).first().toPath())
    }

    private static File loadFileResource(ClassLoader classloader, String resource){
        var resourceUrl = classloader.getResource(resource)
        if (resourceUrl == null) {
            throw new RuntimeException("Test resource not found: $resource")
        }
        return new File(resourceUrl.toURI())
    }
}

class CompilerResult {
    boolean taskResult
    List<Diagnostic> diagnostics
    SortedSet<Path> generatedFiles

    CompilerResult(boolean result, List<Diagnostic> diagnostics, SortedSet<Path> generatedFiles){
        this.taskResult = result
        this.diagnostics = diagnostics
        this.generatedFiles = generatedFiles
    }

    boolean getTaskResult(){
        return taskResult
    }

    List<Diagnostic> getErrors(){
        diagnostics.findAll{it.kind == Diagnostic.Kind.ERROR }
    }

    List<Diagnostic> getWarnings(){
        diagnostics.findAll{it.kind == Diagnostic.Kind.WARNING || it.kind == Diagnostic.Kind.MANDATORY_WARNING }
    }

    boolean wasNoGenerationWarning(){
        getWarnings().find{it.getMessage(Locale.ENGLISH) == LazyvalEnvironment.NO_GENERATION_WARNING}
    }

    boolean wasObjectNotFinalWarning(){
        getWarnings().find{it.getMessage(Locale.ENGLISH) == LazyvalEnvironment.NOT_FINAL_OBJECT_WARNING}
    }

    boolean wasValueNotFinalWarning(){
        getWarnings().find{it.getMessage(Locale.ENGLISH) == LazyvalEnvironment.NOT_FINAL_VALUE_WARNING}
    }

    boolean generatedFile(String s) {
        return generatedFiles.any { (it.fileName.toString() == s) }
    }
}