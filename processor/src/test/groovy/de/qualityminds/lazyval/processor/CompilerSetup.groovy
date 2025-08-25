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

    private JavaCompiler.CompilationTask task
    private LoggingDiagnosticsCollector diagnostics

    private CompilerSetup(JavaCompiler.CompilationTask task, LoggingDiagnosticsCollector diagnostics){
        this.task = task
        this.diagnostics = diagnostics
    }

    CompilerResult run() {
        boolean result = task.call()
        new CompilerResult(result, diagnostics.getDiagnostics())
    }


    static CompilerSetup setupTask(ClassLoader classloader, String fileToCompile, Path tempDir, Libraries libraries){
        var compiler = ToolProvider.getSystemJavaCompiler()
        var diagnostics = new LoggingDiagnosticsCollector<JavaFileObject>()

        var isMapstructDependencyAvailable = libraries == Libraries.ALL || libraries == Libraries.MAPSTRUCT
        var isJpaDependencyAvailable = libraries == Libraries.ALL || libraries == Libraries.JPA

        List<File> additionalClasspath = new ArrayList<>()
        // Add compiled classes from Mill output instead of M2 repository
        additionalClasspath.addAll(MavenResolver.getCoreModuleClasses())

        if(isMapstructDependencyAvailable){
            additionalClasspath.addAll(MavenResolver.resolveDependencies(DEP_MAPSTRUCT, DEP_MAPSTRUCT_PROCESSOR))
        }
        if(isJpaDependencyAvailable){
            additionalClasspath.addAll(MavenResolver.resolveDependencies(DEP_JPA))
        }

        var fileManager = compiler.getStandardFileManager(diagnostics, null, null)
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(tempDir.toFile()))
        fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, Arrays.asList(tempDir.toFile()))

        if(!additionalClasspath.isEmpty()){
            fileManager.setLocation(StandardLocation.CLASS_PATH, additionalClasspath)
        }

        var resourceUrl = classloader.getResource("test/$fileToCompile")
        if (resourceUrl == null) {
            throw new RuntimeException("Test resource not found: test/$fileToCompile")
        }
        var file = new File(resourceUrl.toURI())

        var compilationUnits = fileManager.getJavaFileObjects(file)
        var task = compiler.getTask(null, fileManager, diagnostics, null, null, compilationUnits)
        task.setProcessors(Collections.singletonList(new LazyvalProcessor()))

        new CompilerSetup(task, diagnostics)
    }

    private static File findMillWorkspaceRoot() {
        def currentDir = new File(".").absoluteFile
        while (currentDir != null) {
            if (new File(currentDir, "build.mill").exists()) {
                return currentDir
            }
            currentDir = currentDir.parentFile
        }
        throw new RuntimeException("Could not find Mill workspace root (no build.mill found)")
    }
}

class CompilerResult {
    boolean taskResult
    List<Diagnostic> diagnostics

    CompilerResult(boolean result, List<Diagnostic> diagnostics){
        this.taskResult = result
        this.diagnostics = diagnostics
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
}