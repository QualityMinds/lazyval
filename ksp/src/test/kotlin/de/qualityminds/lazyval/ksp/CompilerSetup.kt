package de.qualityminds.lazyval.ksp

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import de.qualityminds.lazyval.test.MavenResolver
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceLoader
import kotlin.io.path.createDirectories

import org.slf4j.LoggerFactory
import java.util.SortedSet
import kotlin.io.path.isDirectory

data class CompilerResult(
    val kspSuccess: Boolean,
    val kotlinSuccess: Boolean,
    val generatedJavaFiles: SortedSet<Path>,
    val generatedKotlinFiles: SortedSet<Path>
){
    fun isSuccessful() = kspSuccess && kotlinSuccess

    fun printDebugMessages() {
        if(generatedKotlinFiles.isEmpty()){
            println("No Kotlin files generated")
        }else{
            println("Generated Kotlin files: ${generatedKotlinFiles.map{it.toAbsolutePath()}}")
        }
        if(generatedJavaFiles.isEmpty()){
            println("No Java files generated")
        }else {
            println("Generated Java files: ${generatedJavaFiles.map { it.toAbsolutePath() }}")
        }
    }

    fun generatedJavaFile(name: String): Boolean = generatedJavaFiles.any { it.fileName.toString() == name }

    fun generatedKotlinFile(name: String): Boolean = generatedKotlinFiles.any { it.fileName.toString() == name }

}

class CompilerSetup private constructor(private val kspSetup: KotlinSymbolProcessing, private val kotlinSetup: KotlinCompilerSetup) {

    enum class Libraries {
        NONE,
        ALL,
        MAPSTRUCT,
        JPA
    }

    fun run(): CompilerResult {
        val exitCode = kspSetup.execute()
        val kotlinResult = if(exitCode == KotlinSymbolProcessing.ExitCode.OK){
            kotlinSetup.run()
        }else{
            false
        }
        return CompilerResult(
            exitCode == KotlinSymbolProcessing.ExitCode.OK,
            kotlinResult,
            Files.walk((kspSetup.kspConfig as KSPJvmConfig).javaOutputDir.toPath()).filter { p -> !p.isDirectory() }.toList().toSortedSet(),
            Files.walk(kspSetup.kspConfig.kotlinOutputDir.toPath()).filter { p -> !p.isDirectory() }.toList().toSortedSet()
        )
    }

    companion object {

        private val logger = LoggerFactory.getLogger(CompilerSetup::class.java)

        private val DEP_MAPSTRUCT = "org.mapstruct:mapstruct:1.6.3"
        private val DEP_MAPSTRUCT_PROCESSOR = "org.mapstruct:mapstruct-processor:1.6.3"
        private val DEP_JPA = "jakarta.persistence:jakarta.persistence-api:3.2.0"

        fun setupTask(classLoader: ClassLoader, fileToCompile: String, projectDir: Path, libraries: Libraries): CompilerSetup {
            val kspSetup = setupKsp2(classLoader, fileToCompile, projectDir, libraries)
            val kotlinSetup = KotlinCompilerSetup.setup(classLoader, fileToCompile, projectDir, libraries)
            return CompilerSetup(kspSetup, kotlinSetup)
        }

        private fun setupKsp2(classLoader: ClassLoader, fileToCompile: String, projectDir: Path, libraries: Libraries) : KotlinSymbolProcessing{
            val resourceUrl = classLoader.getResource("test/$fileToCompile")
                ?: throw RuntimeException("Test resource not found: test/$fileToCompile")
            val sourceFile = File(resourceUrl.toURI())

            val isMapstructDependencyAvailable = libraries == Libraries.ALL || libraries == Libraries.MAPSTRUCT
            val isJpaDependencyAvailable = libraries == Libraries.ALL || libraries == Libraries.JPA

            val additionalClasspath = mutableListOf<File>()
            if(isMapstructDependencyAvailable){
                additionalClasspath.addAll(MavenResolver.resolveDependencies(DEP_MAPSTRUCT, DEP_MAPSTRUCT_PROCESSOR))
            }
            if(isJpaDependencyAvailable){
                additionalClasspath.addAll(MavenResolver.resolveDependencies(DEP_JPA))
            }

            val symbolProcessorClassloader = URLClassLoader(additionalClasspath.map { it.toURI().toURL() }.toTypedArray(), classLoader)

            val processorProvidersSearch = ServiceLoader.load(
                classLoader.loadClass(
                    "com.google.devtools.ksp.processing.SymbolProcessorProvider"
                ),
                symbolProcessorClassloader
            )

            val compilationUnit = additionalClasspath.toMutableList()
            compilationUnit.addAll(MavenResolver.getCoreModuleClasses())

            @Suppress("UNCHECKED_CAST")
            val processorProviders: List<SymbolProcessorProvider> = processorProvidersSearch.toList() as List<SymbolProcessorProvider>

            val config = KSPJvmConfig.Builder().apply {
                jvmTarget = "17"
                languageVersion = KotlinVersion.CURRENT.toString()
                apiVersion = "${KotlinVersion.CURRENT.major}.${KotlinVersion.CURRENT.minor}"
                moduleName = "test"
                projectBaseDir = projectDir.toFile()
                outputBaseDir = projectDir.resolve("build").createDirectories().toFile()
                classOutputDir = projectDir.resolve("build/classes").createDirectories().toFile()
                javaOutputDir = projectDir.resolve("build/generated/ksp/java").createDirectories().toFile()
                kotlinOutputDir = projectDir.resolve("build/generated/ksp/kotlin").createDirectories().toFile()
                resourceOutputDir = projectDir.resolve("build/generated/ksp/kotlin").createDirectories().toFile()
                cachesDir = projectDir.resolve("build/resources").createDirectories().toFile()
                sourceRoots = listOf(sourceFile)
                // libraries is the actual compilation-unit
                this.libraries = compilationUnit.toList()
            }.build()

            return KotlinSymbolProcessing(
                config,
                processorProviders,
                KspSlf4jLogger())
        }
    }
}