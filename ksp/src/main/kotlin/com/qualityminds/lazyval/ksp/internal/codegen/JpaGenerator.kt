package com.qualityminds.lazyval.ksp.internal.codegen

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import java.util.stream.Stream

// tag::docu[]
class JpaGenerator : Generator {

    companion object {
        private const val GENERATOR_ID = "jpa"
        private const val OPTION_GENERATED_PACKAGE = "lazyval.jpa.package"
    }

    override fun generatorId(): String = GENERATOR_ID

    override fun requiredClasspath(): Collection<String> = listOf("jakarta.persistence.AttributeConverter")

    override fun supportedOptions(): Set<String> {
        return setOf(OPTION_GENERATED_PACKAGE)
    }

    override fun generate(
        validatedElements: NonEmptySet<ValidatedKspGeneratorElement>,
        context: Generator.Context
    ): Stream<GeneratorResult> {
        // end::docu[]

        val packageName = context.generatorPackage("boundary.persistence", OPTION_GENERATED_PACKAGE)

        return validatedElements.stream()
            .map { buildAttributeConverter(it) }
            .map { FileSpec.builder(packageName, it.name!!).addType(it).build() }
            .map { GeneratorResult.Kotlin(
                GeneratorResult.Metadata(it.packageName, it.name),
                it.toString()) }
    }


    private fun buildAttributeConverter(validatedElement: ValidatedKspGeneratorElement): TypeSpec {
        val element = validatedElement.element
        val lazyvalTypeName = element.toClassName()
        val wrappedType = validatedElement.wrappedProperty
        // KotlinPoet specific type-name
        val wrappedTypeName = wrappedType.type.toTypeName()

        val converterClassName = "${validatedElement.typeName}AttributeConverter"

        val convertToDatabaseColumn = buildConvertToDatabaseColumn(lazyvalTypeName, wrappedTypeName, validatedElement)
        val convertToEntityAttribute = buildConvertToEntityAttribute(lazyvalTypeName, wrappedTypeName, validatedElement)

        return TypeSpec.classBuilder(converterClassName)
            .addAnnotation(
                AnnotationSpec.builder(ClassName("jakarta.persistence", "Converter"))
                    .addMember("autoApply = true")
                    .build()
            )
            .addSuperinterface(
                ClassName("jakarta.persistence", "AttributeConverter")
                    .parameterizedBy(
                        lazyvalTypeName.copy(nullable = true),
                        wrappedTypeName.copy(nullable = true)
                    )
            )
            .addFunction(convertToDatabaseColumn)
            .addFunction(convertToEntityAttribute)
            .build()
    }

    private fun buildConvertToDatabaseColumn(
        lazyvalTypeName: ClassName,
        wrappedTypeName: TypeName,
        validatedElement: ValidatedKspGeneratorElement
    ): FunSpec {
        val convertToDatabaseColumn = FunSpec.builder("convertToDatabaseColumn")
            .addModifiers(KModifier.OVERRIDE)
            .returns(wrappedTypeName.copy(nullable = true))
            .addParameter("type", lazyvalTypeName.copy(nullable = true))
            .apply {
                addStatement("return type?.${validatedElement.kotlinAccessor}")
            }
            .build()
        return convertToDatabaseColumn
    }

    private fun buildConvertToEntityAttribute(
        lazyvalTypeName: ClassName,
        wrappedTypeName: TypeName,
        validatedElement: ValidatedKspGeneratorElement
    ): FunSpec {
        val parameterName = "dbValue"
        // Build convertToEntityAttribute method
        val convertToEntityAttribute = FunSpec.builder("convertToEntityAttribute")
            .addModifiers(KModifier.OVERRIDE)
            .returns(lazyvalTypeName.copy(nullable = true))
            .addParameter(parameterName, wrappedTypeName.copy(nullable = true))
            .apply {
                addStatement("return $parameterName?.let { ${validatedElement.objectCreation(parameterName)} }")
            }
            .build()
        return convertToEntityAttribute
    }
}