package com.acme.lazyval.generator

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.SingleFileGenerator
import com.qualityminds.lazyval.ksp.spi.SpiGenerator
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import java.util.Collections.emptyList

class SingleFileUtilsGenerator : SingleFileGenerator {


    companion object {
        const val OPTION_GENERATED_PACKAGE = "acme.some.lazyval.generatedPackage"
    }

    override fun generatorId(): String = "acme-utils-single"

    override fun requiredClasspath(): Collection<String> = emptyList()

    override fun supportedOptions(): Set<String> {
        return setOf(OPTION_GENERATED_PACKAGE)
    }

    override fun generateSingleFile(
        validatedElements: NonEmptySet<ValidatedKspGeneratorElement>,
        userSettings: SpiGenerator.Settings
    ): GeneratorResult {

        val imports: MutableList<String> = mutableListOf()
        val methods: MutableList<String> = mutableListOf()

        validatedElements.stream()
            .filter { ve: ValidatedKspGeneratorElement ->
                "String" == ve.wrappedProperty.type.toString()
            }
            .forEach { validatedElement: ValidatedKspGeneratorElement ->
                val element = validatedElement.element
                val typeName = element.simpleName.asString()
                imports.add("import ${element.qualifiedName?.asString()}")
                val method = "public fun ${typeName}.toUpperCase2(): String = this.${validatedElement.kotlinAccessor}.uppercase()"
                methods.add(method)
            }

        val packageName = userSettings.options[OPTION_GENERATED_PACKAGE]
            // any element suffices to create the package
            ?: "${extractRootPackage(validatedElements.any.element)}.test"

        val contents = """
            |package $packageName
|
            |${imports.joinToString("\n")}
            |import kotlin.String
|
            |${methods.joinToString("\n")}
        """.trimMargin().replace("\n", System.lineSeparator())

        return GeneratorResult.Kotlin(
            GeneratorResult.Metadata(packageName, "Utils"),
            contents)
    }
}
