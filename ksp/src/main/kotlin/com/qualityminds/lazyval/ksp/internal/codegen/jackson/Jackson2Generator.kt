package com.qualityminds.lazyval.ksp.internal.codegen.jackson

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import java.util.stream.Stream

class Jackson2Generator : Generator {

    companion object {
        private const val GENERATOR_ID = "jackson-2"
        private const val OPTION_GENERATED_PACKAGE = "lazyval.jackson.package"
        private val VERSION = JacksonVersion.JACKSON_2
    }

    private val codegen = JacksonCodegen(VERSION)

    override fun generatorId(): String = GENERATOR_ID

    override fun requiredClasspath(): Collection<String> = listOf("com.fasterxml.jackson.databind.Module")

    override fun supportedOptions(): Set<String> = setOf(OPTION_GENERATED_PACKAGE)

    @Suppress("DuplicatedCode")
    override fun generate(
        validatedElements: NonEmptySet<ValidatedKspGeneratorElement>,
        context: Generator.Context
    ): Stream<GeneratorResult> {
        val isQuarkus = context.isOnClasspath("io.quarkus.jackson.ObjectMapperCustomizer")

        val serializers = mutableListOf<TypeSpec>()
        val deserializers = mutableListOf<TypeSpec>()
        val elementTypes = mutableListOf<ClassName>()
        validatedElements.forEach { element ->
            serializers += codegen.generateSerializer(element)
            deserializers += codegen.generateDeserializer(element)
            elementTypes += element.element.toClassName()
        }

        val typeSpec = codegen.generateModule(serializers, deserializers, elementTypes, isQuarkus)

        val jacksonModulePackage = context.generatorPackage(null, OPTION_GENERATED_PACKAGE)

        val fileSpec = FileSpec.builder(jacksonModulePackage, typeSpec.name!!)
            .addType(typeSpec)
            .build()

        val fileMetadata = GeneratorResult.Metadata(fileSpec.packageName, fileSpec.name)

        return Stream.of(
            GeneratorResult.ServiceLoader(
                GeneratorResult.Metadata(VERSION.spiPackage, VERSION.spiClass),
                fileMetadata
            ),
            GeneratorResult.Kotlin(fileMetadata, fileSpec.toString())
        )
    }
}
