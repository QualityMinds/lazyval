package com.acme.lazyval.generator

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import java.util.Collections.emptyList
import java.util.stream.Stream

class UtilsGenerator : Generator {


    companion object {
        const val OPTION_GENERATED_PACKAGE = "acme.some.lazyval.generatedPackage"
    }

    override fun generatorId(): String = "acme-utils-single"

    override fun requiredClasspath(): Collection<String> = emptyList()

    override fun supportedOptions(): Set<String> {
        return setOf(OPTION_GENERATED_PACKAGE)
    }

    override fun generate(
        validatedElements: NonEmptySet<ValidatedKspGeneratorElement>,
        ctx: Generator.Context
    ): Stream<GeneratorResult> {

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

        val packageName = ctx.generatorPackage(null, OPTION_GENERATED_PACKAGE)

        val contents = """
            |package $packageName
            |
            |${imports.joinToString("\n")}
            |import kotlin.String
            |
            |${methods.joinToString("\n")}
        """.trimMargin().replace("\n", System.lineSeparator())

        return Stream.of(GeneratorResult.Kotlin(
            GeneratorResult.Metadata(packageName, "Utils"),
            contents))
    }
}
