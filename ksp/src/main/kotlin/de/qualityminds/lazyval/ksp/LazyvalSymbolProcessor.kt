package de.qualityminds.lazyval.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.FileSpec
import de.qualityminds.lazyval.LazyValue

class LazyvalSymbolProcessor(
    private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {

    private lateinit var lazyvalEnvironment: LazyvalKspEnvironment

    override fun process(resolver: Resolver): List<KSAnnotated> {
        try {
            lazyvalEnvironment = LazyvalKspEnvironment(environment, resolver)
        } catch (_: IllegalArgumentException) {
            return emptyList()
        }

        if (!lazyvalEnvironment.isJpaOnClasspath() && !lazyvalEnvironment.isMapstructOnClasspath()) {
            lazyvalEnvironment.warnMissingClasspath()
            return emptyList()
        }

        environment.logger.info("Files: ${resolver.getAllFiles().toList().map { it.fileName }}")
        val annotatedSymbols: Sequence<KSAnnotated> = resolver.getSymbolsWithAnnotation(LazyValue::class.qualifiedName!!)

        val validatedElements = annotatedSymbols
            .filterIsInstance<KSClassDeclaration>()
            .mapNotNull { classDecl ->
                lazyvalEnvironment.validateElement(classDecl)?.let { validated ->
                    classDecl to validated
                }
            }
            .toList()

        // Generate JPA AttributeConverters (Kotlin)
        if (lazyvalEnvironment.isJpaOnClasspath()) {
            validatedElements.forEach { (classDecl, element) ->
                val fileSpec = JpaKspGenerator.createJpaAttributeConverter(element, lazyvalEnvironment)
                writeKotlinFile(fileSpec, classDecl.containingFile)
            }
        } else {
            lazyvalEnvironment.info("JPA is not on classpath. Lazyval will not generate AttributeConverters.")
        }

        // Generate Mapstruct Mapper (Java)
        if (lazyvalEnvironment.isMapstructOnClasspath() && validatedElements.isNotEmpty()) {
            val elements = validatedElements.map { it.second }
            val javaFileSpec = MapstructKspGenerator.createMapstructMapper(elements, lazyvalEnvironment)
            val sourceFiles = validatedElements.mapNotNull { it.first.containingFile }
            writeJavaFile(javaFileSpec, sourceFiles)
        } else if (!lazyvalEnvironment.isMapstructOnClasspath()) {
            lazyvalEnvironment.info("Mapstruct is not on classpath. Lazyval will not generate Mapstruct mappers.")
        }

        return emptyList()
    }

    private fun writeKotlinFile(fileSpec: FileSpec, sourceFile: KSFile?) {
        try {
            val dependencies = if (sourceFile != null) {
                Dependencies(true, sourceFile)
            } else {
                Dependencies(false)
            }

            val file = environment.codeGenerator.createNewFile(
                dependencies = dependencies,
                packageName = fileSpec.packageName,
                fileName = fileSpec.name,
                extensionName = "kt"
            )
            file.write(fileSpec.toString().toByteArray())
            file.close()
            lazyvalEnvironment.info("Written Kotlin file '${fileSpec.packageName}.${fileSpec.name}'")
        } catch (e: Exception) {
            lazyvalEnvironment.error("Failed to write Kotlin file: ${e.message}")
            throw e
        }
    }

    // Since only one mapper file is generated for all sources, we pass in all sources to have
    // proper regeneration on incremental builds.
    private fun writeJavaFile(javaFileSpec: JavaFileSpec, sourceFiles: List<KSFile>) {
        try {
            val dependencies = if (sourceFiles.isNotEmpty()) {
                Dependencies(true, *sourceFiles.toTypedArray())
            } else {
                Dependencies(false)
            }

            javaFileSpec.writeTo(environment.codeGenerator, dependencies)
            lazyvalEnvironment.info("Written Java file '${javaFileSpec.packageName}.${javaFileSpec.fileName}'")
        } catch (e: Exception) {
            lazyvalEnvironment.error("Failed to write Java file: ${e.message}")
            throw e
        }
    }
}