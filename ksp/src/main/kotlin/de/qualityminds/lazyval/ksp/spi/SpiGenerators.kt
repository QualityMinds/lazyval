package de.qualityminds.lazyval.ksp.spi

import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.FileSpec
import de.qualityminds.lazyval.ksp.LazyvalKspEnvironment
import de.qualityminds.lazyval.ksp.ValidatedKspGeneratorElement
import de.qualityminds.lazyval.ksp.codegen.JavaFileSpec

/**
 * Internal use only.
 */
data class ValidateElementWithSource(val element: ValidatedKspGeneratorElement, val source: KSFile)


// tag::result[]
sealed interface GeneratorResult {
    data class Kotlin(val fileSpec: FileSpec) : GeneratorResult
    data class Java(val fileSpec: JavaFileSpec) : GeneratorResult
    object Nothing : GeneratorResult
}
// end::result[]

sealed interface SpiGenerator {
    /**
     * A short id/name of the generator. The id must only contain valid Java package characters.
     *
     *
     * The id is used to extract config options from the processing-environment in the form of
     * *"lazyval.generatorId.optionA"* or to disable the generator via the option
     * *<i>"lazyval.disabledGenerators"*.
     *
     */
    fun generatorId(): String

    /**
     * If the generator requires additional classes on the classpath, they can be listed here.
     * The processor will check if all required classes are available and only call the generator if this is the case.
     * @return list containing fully qualified class names.
     */
    fun requiredClasspath(): Collection<String>


    fun KSType.isPrimitive(): Boolean {
        return when (declaration.simpleName.asString()) {
            "Int", "Long", "Short", "Byte", "Double", "Float", "Boolean", "Char" -> true
            else -> false
        }
    }

    fun KSType.isBoxedPrimitive(): Boolean {
        return when (declaration.qualifiedName?.asString()) {
            "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte",
            "java.lang.Double", "java.lang.Float", "java.lang.Boolean", "java.lang.Character" -> true
            else -> false
        }
    }

    /**
     * Settings provided by the user for this generator.
     * The map will only contain keys which have the current generators id infixed, e.g. *"lazyval.generatorId.optionA"*
     */
    data class Settings(val options: Map<String, String>)
}

/**
 * A service provider interface which is called by the [de.qualityminds.lazyval.ksp.LazyvalSymbolProcessor] to generate
 * a file per domain primitive annotated with [de.qualityminds.lazyval.LazyValued].
 *
 * As an example, for each domain primitive a dedicated JPA AttributeConverter is needed.
 */
interface MultipleFilesGenerator : SpiGenerator {

    /**
     * Called for each domain primitive annotated with [de.qualityminds.lazyval.LazyValue].
     * @param validatedElement the element annotated with [de.qualityminds.lazyval.LazyValue]
     * @param userSettings provided [Settings]
     */
    fun generateFilePerType(
        validatedElement: ValidatedKspGeneratorElement,
        userSettings: SpiGenerator.Settings
    ): GeneratorResult
}

/**
 * A service provider interface which is called by the [de.qualityminds.lazyval.ksp.LazyvalSymbolProcessor] to generate
 * a single file for all domain primitives annotated with [de.qualityminds.lazyval.LazyValued].
 *
 * As an example, for all domain primitives only a single Mapstruct Mapper definition is needed.
 *
 * The list of elements is guaranteed to be non-empty.
 */
interface SingleFileGenerator : SpiGenerator {

    /**
     * Called only once with a list of all domain primitives annotated with [de.qualityminds.lazyval.LazyValue].
     * @param validatedElements all elements annotated with [de.qualityminds.lazyval.LazyValue]. Guaranteed to be non-empty.
     * @param userSettings provided [Settings]
     */
    fun generateSingleFile(
        validatedElements: NonEmptySet<ValidatedKspGeneratorElement>,
        userSettings: SpiGenerator.Settings
    ): GeneratorResult
}

