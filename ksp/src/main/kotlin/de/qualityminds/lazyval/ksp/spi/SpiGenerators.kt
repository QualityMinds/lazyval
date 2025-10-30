package de.qualityminds.lazyval.ksp.spi

import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.FileSpec
import de.qualityminds.lazyval.ksp.LazyvalKspEnvironment
import de.qualityminds.lazyval.ksp.ValidatedKspGeneratorElement
import de.qualityminds.lazyval.ksp.codegen.JavaFileSpec

/**
 * Internal use only.
 */
data class ValidateElementWithSource(val element: ValidatedKspGeneratorElement, val source: KSFile)


sealed interface GeneratorResult {
    data class Kotlin(val fileSpec: FileSpec) : GeneratorResult
    data class Java(val fileSpec: JavaFileSpec) : GeneratorResult
    object Nothing : GeneratorResult
}

sealed interface SpiGenerator

/**
 * A service provider interface which is called by the [de.qualityminds.lazyval.ksp.LazyvalSymbolProcessor] to generate
 * a file per domain primitive annotated with [de.qualityminds.lazyval.LazyValued].
 *
 * As an example, for each domain primitive a dedicated JPA AttributeConverter is needed.
 */
interface MultipleFilesGenerator : SpiGenerator {

    fun generateFilePerType(
        validatedElement: ValidatedKspGeneratorElement,
        environment: LazyvalKspEnvironment
    ): GeneratorResult
}

/**
 * A service provider interface which is called by the [de.qualityminds.lazyval.ksp.LazyvalSymbolProcessor] to generate
 * a single file for all domain primitives annotated with [de.qualityminds.lazyval.LazyValued].
 *
 * As an example, for all domain primitives only a single Mapstruct Mapper definition is needed.
 */
interface SingleFileGenerator : SpiGenerator {

    fun generateSingleFile(
        validatedElements: List<ValidatedKspGeneratorElement>,
        environment: LazyvalKspEnvironment
    ): GeneratorResult
}

