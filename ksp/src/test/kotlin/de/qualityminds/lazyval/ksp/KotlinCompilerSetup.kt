package de.qualityminds.lazyval.ksp

import org.jetbrains.kotlin.buildtools.api.CompilationResult
import org.jetbrains.kotlin.buildtools.api.CompilationService
import org.jetbrains.kotlin.buildtools.api.CompilerExecutionStrategyConfiguration
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.ProjectId
import org.jetbrains.kotlin.buildtools.api.jvm.JvmCompilationConfiguration
import de.qualityminds.lazyval.test.MavenResolver
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories

@OptIn(ExperimentalBuildToolsApi::class)
class KotlinCompilerSetup private constructor(
    val projectDir: Path,
    val service: CompilationService,
    val strategyConfig: CompilerExecutionStrategyConfiguration,
    val compilationConfig: JvmCompilationConfiguration,
    val sources: List<File>,
    val compilerClasspath: List<File>
) {
    private fun findAllSourceFiles(file: File): List<File> =
        if (file.isDirectory) {
            file.listFiles()?.flatMap(::findAllSourceFiles) ?: emptyList()
        } else {
            listOf(file)
        }

    fun run(): Boolean {

        // make sure to also compile the generated sources from KSP
        // because the sources are not available during setup, we have to defer this to run()
        val kspJavaSources = findAllSourceFiles(projectDir.resolve("build/generated/ksp/java").toFile())
        val kspKotlinSources = findAllSourceFiles(projectDir.resolve("build/generated/ksp/kotlin").toFile())

        val result = service.compileJvm(
            ProjectId.RandomProjectUUID(),
            strategyConfig,
            compilationConfig,
            sources + kspJavaSources + kspKotlinSources,
            listOf(
                // destination directory
                "-d", projectDir.resolve("build/classes").toAbsolutePath().toString(),
                // classpath
                "-classpath", compilerClasspath.joinToString(File.pathSeparator) { it.absolutePath },
                // std-lib and kotlin-reflect will be added to classpath manually
                "-no-stdlib", "-no-reflect"
                )
        )
        return when(result) {
            CompilationResult.COMPILATION_SUCCESS -> true
            CompilationResult.COMPILATION_ERROR -> false
            CompilationResult.COMPILATION_OOM_ERROR -> false
            CompilationResult.COMPILER_INTERNAL_ERROR -> false
        }
    }

    companion object {
        private val DEP_MAPSTRUCT = "org.mapstruct:mapstruct:1.6.3"
        private val DEP_MAPSTRUCT_PROCESSOR = "org.mapstruct:mapstruct-processor:1.6.3"
        private val DEP_JPA = "jakarta.persistence:jakarta.persistence-api:3.2.0"


        fun setup(classLoader: ClassLoader, fileToCompile: String, projectDir: Path, libraries: ToolchainSetup.Libraries) : KotlinCompilerSetup {
            val idGeneratorSource = ToolchainSetup.loadFileResource(classLoader, "util/IdGenerator.kt")
            val sourceFile = ToolchainSetup.loadFileResource(classLoader, "test/$fileToCompile")

            val isMapstructDependencyAvailable = libraries == ToolchainSetup.Libraries.ALL || libraries == ToolchainSetup.Libraries.MAPSTRUCT
            val isJpaDependencyAvailable = libraries == ToolchainSetup.Libraries.ALL || libraries == ToolchainSetup.Libraries.JPA

            val compilerClasspath = MavenResolver.resolveDependencies(
                "org.jetbrains.kotlin:kotlin-stdlib:${KotlinVersion.CURRENT}",
                "org.jetbrains.kotlin:kotlin-reflect:${KotlinVersion.CURRENT}"
            ).toMutableList()
            compilerClasspath.addAll(MavenResolver.getCoreModuleClasses())
            if(isMapstructDependencyAvailable){
                compilerClasspath.addAll(MavenResolver.resolveDependencies(DEP_MAPSTRUCT, DEP_MAPSTRUCT_PROCESSOR))
                // TODO add Mapper to sources
            }
            if(isJpaDependencyAvailable){
                compilerClasspath.addAll(MavenResolver.resolveDependencies(DEP_JPA))
                // TODO add Converter to sources
            }

            val service = CompilationService.loadImplementation(classLoader)
            val strategyConfig = service.makeCompilerExecutionStrategyConfiguration()
            val compilationConfig = service.makeJvmCompilationConfiguration().apply {
                val incrementalConfig = this.makeClasspathSnapshotBasedIncrementalCompilationConfiguration()
                incrementalConfig.setRootProjectDir(projectDir.toFile())
                incrementalConfig.setBuildDir(projectDir.resolve("build").createDirectories().toFile())
                incrementalConfig.usePreciseJavaTracking(true)
                this.useLogger(KotlinCompilerSlf4jLogger())
            }

            return KotlinCompilerSetup(
                projectDir,
                service,
                strategyConfig,
                compilationConfig,
                listOf(idGeneratorSource, sourceFile),
                compilerClasspath.toList())
        }
    }

}