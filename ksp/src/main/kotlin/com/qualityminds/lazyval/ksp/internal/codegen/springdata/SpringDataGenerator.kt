package com.qualityminds.lazyval.ksp.internal.codegen.springdata

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import java.util.stream.Stream

class SpringDataGenerator : Generator {

    companion object {
        private const val GENERATOR_ID = "spring-data"
        private const val OPTION_GENERATED_PACKAGE = "lazyval.spring_data.package"

        private val READING_CONVERTER = ClassName("org.springframework.data.convert", "ReadingConverter")
        private val WRITING_CONVERTER = ClassName("org.springframework.data.convert", "WritingConverter")
        private val CONVERTER = ClassName("org.springframework.core.convert.converter", "Converter")
        private val CONFIGURATION = ClassName("org.springframework.context.annotation", "Configuration")
        private val BEAN = ClassName("org.springframework.context.annotation", "Bean")
        private val CASSANDRA_CUSTOM_CONVERSIONS = ClassName(
            "org.springframework.data.cassandra.core.convert", "CassandraCustomConversions"
        )
    }

    override fun generatorId(): String = GENERATOR_ID

    override fun requiredClasspath(): Collection<String> =
        listOf("org.springframework.data.convert.ReadingConverter")

    override fun supportedOptions(): Set<String> = setOf(OPTION_GENERATED_PACKAGE)

    override fun generate(
        validatedElements: NonEmptySet<ValidatedKspGeneratorElement>,
        context: Generator.Context
    ): Stream<GeneratorResult> {
        if (!context.isOnClasspath("org.springframework.data.cassandra.core.convert.CassandraCustomConversions")) {
            return Stream.empty()
        }

        val converterPackage = context.generatorPackage(OPTION_GENERATED_PACKAGE, "boundary.persistence.cassandra")

        val converterSpecs = mutableListOf<TypeSpec>()
        for (element in validatedElements) {
            converterSpecs += buildReadConverter(element)
            converterSpecs += buildWriteConverter(element)
        }

        val configSpec = buildSpringDataConfiguration(converterSpecs, context)

        val file = FileSpec.builder(converterPackage, "LazyvalSpringDataConfiguration")
            .addType(configSpec)
            .apply { converterSpecs.forEach { addType(it) } }
            .build()

        return Stream.of(GeneratorResult.Kotlin(
            GeneratorResult.Metadata(file.packageName, file.name),
            file.toString()
        ))
    }

    private fun buildReadConverter(element: ValidatedKspGeneratorElement): TypeSpec {
        val elementClassName = element.element.toClassName()
        val wrappedTypeName = element.wrappedProperty.type.toTypeName()
        val factoryReturnsNullable = element.factoryMethod?.returnType?.resolve()?.isMarkedNullable ?: false
        val returnType = elementClassName.copy(nullable = factoryReturnsNullable)

        return TypeSpec.classBuilder("${element.typeName}ReadConverter")
            .addModifiers(KModifier.PRIVATE)
            .addAnnotation(READING_CONVERTER)
            .addSuperinterface(
                CONVERTER.parameterizedBy(wrappedTypeName.copy(nullable = false), returnType)
            )
            .addFunction(
                FunSpec.builder("convert")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("source", wrappedTypeName.copy(nullable = false))
                    .returns(returnType)
                    .addStatement("return ${element.objectCreation("source")}")
                    .build()
            )
            .build()
    }

    private fun buildWriteConverter(element: ValidatedKspGeneratorElement): TypeSpec {
        val elementClassName = element.element.toClassName()
        val wrappedTypeName = element.wrappedProperty.type.toTypeName()

        return TypeSpec.classBuilder("${element.typeName}WriteConverter")
            .addModifiers(KModifier.PRIVATE)
            .addAnnotation(WRITING_CONVERTER)
            .addSuperinterface(
                CONVERTER.parameterizedBy(elementClassName, wrappedTypeName.copy(nullable = false))
            )
            .addFunction(
                FunSpec.builder("convert")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("source", elementClassName)
                    .returns(wrappedTypeName.copy(nullable = false))
                    .addStatement("return source.${element.kotlinAccessor}")
                    .build()
            )
            .build()
    }

    private fun buildSpringDataConfiguration(converterSpecs: List<TypeSpec>, context: Generator.Context): TypeSpec {
        val hasConditionalOnMissingBean = context.isOnClasspath(
            "org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean"
        )

        val beanMethod = FunSpec.builder("cassandraCustomConversions")
            .addAnnotation(BEAN)
            .returns(CASSANDRA_CUSTOM_CONVERSIONS)
            .apply {
                if (hasConditionalOnMissingBean) {
                    addAnnotation(
                        ClassName("org.springframework.boot.autoconfigure.condition", "ConditionalOnMissingBean")
                    )
                }
            }
            .addStatement(
                "val converters = listOf(\n%L\n)",
                converterSpecs.joinToString(",\n") { "    ${it.name}()" }
            )
            .addStatement("return %T(converters)", CASSANDRA_CUSTOM_CONVERSIONS)
            .build()

        return TypeSpec.classBuilder("LazyvalSpringDataConfiguration")
            .addAnnotation(CONFIGURATION)
            .addKdoc("""
                Generated Spring Data converter configuration.

                Registers all read/write converters for types annotated with `@Lazyval`
                with the appropriate Spring Data store-specific conversion service.

                Generated by the Lazyval annotation processor. Do not modify.
            """.trimIndent())
            .addFunction(beanMethod)
            .build()
    }
}
