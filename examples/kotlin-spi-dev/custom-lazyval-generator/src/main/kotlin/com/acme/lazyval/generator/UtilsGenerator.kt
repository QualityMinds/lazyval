package com.acme.lazyval.generator

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import java.util.Collections.emptySet
import java.util.stream.Stream

class UtilsGenerator : Generator {


    companion object {
        const val OPTION_GENERATED_PACKAGE = "acme.some.lazyval.generatedPackage"
    }

    override fun generatorId(): String = "acme-utils-single"

    override fun requiredClasspath(): Set<String> = emptySet()

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
            // payloadType, not the payload as declared: a `value class` over String is carried as a
            // String at runtime, and filtering on the declaration would skip it.
            .filter { ve: ValidatedKspGeneratorElement ->
                "String" == ve.payloadType.declaration.simpleName.asString()
            }
            .forEach { validatedElement: ValidatedKspGeneratorElement ->
                val element = validatedElement.element
                val typeName = element.simpleName.asString()
                imports.add("import ${element.qualifiedName?.asString()}")
                val method = "public fun ${typeName}.toUpperCase2(): String = ${validatedElement.kotlin.read("this")}.uppercase()"
                methods.add(method)
            }

        val packageName = ctx.generatorPackage( OPTION_GENERATED_PACKAGE, null)

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
