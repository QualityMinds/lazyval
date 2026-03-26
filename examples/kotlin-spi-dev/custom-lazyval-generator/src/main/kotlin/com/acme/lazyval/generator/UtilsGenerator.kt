package com.acme.lazyval.generator

import com.qualityminds.lazyval.ksp.spi.FilePerTypeGenerator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.SpiGenerator
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import java.util.Collections.emptyList

class UtilsGenerator : FilePerTypeGenerator {


    companion object {
        const val OPTION_GENERATED_PACKAGE = "acme.some.lazyval.generatedPackage"
    }

    override fun generatorId(): String = "acme-utils"

    override fun requiredClasspath(): Collection<String> = emptyList()

    override fun supportedOptions(): Set<String> {
        return setOf(OPTION_GENERATED_PACKAGE)
    }

    override fun generateFilePerType(
        validatedElement: ValidatedKspGeneratorElement,
        userSettings: SpiGenerator.Settings
    ): GeneratorResult {
        val element = validatedElement.element

        // this generator should only handle String types
        if ("String" != validatedElement.wrappedProperty.type.toString()) {
            return GeneratorResult.Nothing
        }

        val typeName = element.simpleName.asString()
        val className = "${typeName}Utils"
        val packageName = userSettings.options[OPTION_GENERATED_PACKAGE]
            ?: "${extractRootPackage(element)}.test"

        val contents = """
            package $packageName
            
            import ${element.qualifiedName?.asString()}
            import kotlin.String
            
            public fun ${typeName}.toUpperCase(): String = this.${validatedElement.kotlinAccessor}.uppercase()
        """.trimIndent().replace("\n", System.lineSeparator())

        return GeneratorResult.Kotlin(
            GeneratorResult.Metadata(packageName, className),
            contents)
    }

}
