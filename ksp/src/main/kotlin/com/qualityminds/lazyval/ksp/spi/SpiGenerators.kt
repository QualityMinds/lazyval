package com.qualityminds.lazyval.ksp.spi

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.ValidatedKspGeneratorElement
import com.qualityminds.lazyval.ksp.spi.SpiGenerator.Settings
import org.jetbrains.annotations.ApiStatus

/**
 * Internal use only.
 */
@ApiStatus.Internal
sealed interface ValidateElement {
    val element: ValidatedKspGeneratorElement

    data class ValidatedSourceElement(override val element: ValidatedKspGeneratorElement, val source: KSFile) : ValidateElement
    data class ValidatedJarElement(override val element: ValidatedKspGeneratorElement) : ValidateElement
}

// tag::result[]
/**
 * The result of a generator invocation. The processor will write the actual file based on the result.
 */
sealed interface GeneratorResult {
    /**
     * Describes a to-be generated Kotlin file.
     * @param metadata metadata about the to-be generated file
     * @param contents the generated code
     */
    data class Kotlin(val metadata: Metadata, val contents: String) : GeneratorResult
    /**
     * Describes a to-be generated Java file.
     * @param metadata metadata about the to-be generated file
     * @param contents the generated code
     */
    data class Java(val metadata: Metadata, val contents: String) : GeneratorResult
    /**
     * Can be used to signal that no should be generated (for instance, when the generator only handles
     * wrapped values of a certain type)
     */
    object Nothing : GeneratorResult

    /**
     * Provides metadata about generated files.
     * @param packageName the package name of the generated file
     * @param className the name of the generated file
     */
    data class Metadata(val packageName: String, val className: String)
}
// end::result[]

/**
 * Common properties for all SPI generators.
 */
@ApiStatus.Experimental
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

    /**
     * Provides the keys supported by this generator. At least an option to specify the target package should be
     * listed here.
     * The key must contain "lazyval." at some place in the key to distinguish it from other processors
     * @return set of supported options.
     */
    fun supportedOptions(): Set<String>


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
     * Split the elements FQN into its constituents and returns the substring before the "layer-markers",
     * which will be the root package. If nothing matches, the whole FQN will be returned.
     */
    fun extractRootPackage(classDeclaration: KSClassDeclaration): String {
        val layerPackages = setOf("boundary", "control", "entity", "application", "infrastructure", "domain")
        val packageParts = classDeclaration.packageName.asString().split(".")
        return packageParts.takeWhile { part ->
            !layerPackages.contains(part) && !part.first().isUpperCase()
        }.joinToString(".")
    }

    /**
     * Settings provided by the user for this generator.
     * The map will only contain keys which have the current generators id infixed, e.g. *"lazyval.generatorId.optionA"*
     */
    data class Settings(val options: Map<String, String>)
}

/**
 * A service provider interface which is called to generate a file per domain primitive.
 *
 * As an example, for each domain primitive a dedicated JPA AttributeConverter is needed.
 */
@ApiStatus.Experimental
interface FilePerTypeGenerator : SpiGenerator {

    /**
     * Called for each domain primitive annotated with [com.qualityminds.lazyval.LazyValue].
     * @param validatedElement the element annotated with [com.qualityminds.lazyval.LazyValue]
     * @param userSettings provided [Settings]
     */
    fun generateFilePerType(
        validatedElement: ValidatedKspGeneratorElement,
        userSettings: Settings
    ): GeneratorResult
}

/**
 * A service provider interface which is called to generate
 * a single file for all domain primitives.
 *
 * As an example, for all domain primitives only a single Mapstruct Mapper definition is needed.
 *
 * The list of elements is guaranteed to be non-empty.
 */
@ApiStatus.Experimental
interface SingleFileGenerator : SpiGenerator {

    /**
     * Called only once with a list of all domain primitives annotated with [com.qualityminds.lazyval.LazyValue].
     * @param validatedElements all elements annotated with [com.qualityminds.lazyval.LazyValue]. Guaranteed to be non-empty.
     * @param userSettings provided [Settings]
     */
    fun generateSingleFile(
        validatedElements: NonEmptySet<ValidatedKspGeneratorElement>,
        userSettings: Settings
    ): GeneratorResult
}

