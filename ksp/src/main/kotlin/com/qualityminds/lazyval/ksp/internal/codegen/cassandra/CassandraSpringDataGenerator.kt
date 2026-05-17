package com.qualityminds.lazyval.ksp.internal.codegen.cassandra

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import java.util.stream.Stream

class CassandraSpringDataGenerator : Generator {

    companion object {
        private const val GENERATOR_ID = "cassandra-spring-data"
        private const val OPTION_GENERATED_PACKAGE = "lazyval.cassandra_spring_data.package"

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
        listOf("org.springframework.data.cassandra.core.convert.CassandraCustomConversions")

    override fun supportedOptions(): Set<String> = setOf(OPTION_GENERATED_PACKAGE)

    override fun generate(
        validatedElements: NonEmptySet<ValidatedKspGeneratorElement>,
        context: Generator.Context
    ): Stream<GeneratorResult> {
        val converterPackage = context.generatorPackage(OPTION_GENERATED_PACKAGE, "boundary.persistence.cassandra")

        val results = mutableListOf<GeneratorResult>()
        val converterClassNames = mutableListOf<String>()

        for (element in validatedElements) {
            val readConverter = buildReadConverter(element)
            val writeConverter = buildWriteConverter(element)

            val readFile = FileSpec.builder(converterPackage, readConverter.name!!)
                .addType(readConverter)
                .build()
            val writeFile = FileSpec.builder(converterPackage, writeConverter.name!!)
                .addType(writeConverter)
                .build()

            results += GeneratorResult.Kotlin(
                GeneratorResult.Metadata(readFile.packageName, readFile.name),
                readFile.toString()
            )
            results += GeneratorResult.Kotlin(
                GeneratorResult.Metadata(writeFile.packageName, writeFile.name),
                writeFile.toString()
            )

            converterClassNames += readConverter.name!!
            converterClassNames += writeConverter.name!!
        }

        if (converterClassNames.isNotEmpty()) {
            val configSpec = buildConfiguration(converterClassNames, context)
            val configFile = FileSpec.builder(converterPackage, configSpec.name!!)
                .addType(configSpec)
                .build()
            results += GeneratorResult.Kotlin(
                GeneratorResult.Metadata(configFile.packageName, configFile.name),
                configFile.toString()
            )
        }

        return results.stream()
    }

    private fun buildReadConverter(element: ValidatedKspGeneratorElement): TypeSpec {
        val elementClassName = element.element.toClassName()
        val wrappedTypeName = element.wrappedProperty.type.toTypeName()

        return TypeSpec.classBuilder("${element.typeName}ReadConverter")
            .addAnnotation(READING_CONVERTER)
            .addSuperinterface(
                CONVERTER.parameterizedBy(wrappedTypeName.copy(nullable = false), elementClassName)
            )
            .addFunction(
                FunSpec.builder("convert")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("source", wrappedTypeName.copy(nullable = false))
                    .returns(elementClassName)
                    .addStatement("return ${element.objectCreation("source")}${nullAssert(element)}")
                    .build()
            )
            .build()
    }

    private fun buildWriteConverter(element: ValidatedKspGeneratorElement): TypeSpec {
        val elementClassName = element.element.toClassName()
        val wrappedTypeName = element.wrappedProperty.type.toTypeName()

        return TypeSpec.classBuilder("${element.typeName}WriteConverter")
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

    private fun nullAssert(element: ValidatedKspGeneratorElement): String {
        val returnType = element.factoryMethod?.returnType?.resolve() ?: return ""
        return if (returnType.isMarkedNullable) "!!" else ""
    }

    private fun buildConfiguration(converterClassNames: List<String>, context: Generator.Context): TypeSpec {
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
                converterClassNames.joinToString(",\n") { "    ${it}()" }
            )
            .addStatement("return %T(converters)", CASSANDRA_CUSTOM_CONVERSIONS)
            .build()

        return TypeSpec.classBuilder("LazyvalCassandraSpringDataConfiguration")
            .addAnnotation(CONFIGURATION)
            .addFunction(beanMethod)
            .build()
    }
}
