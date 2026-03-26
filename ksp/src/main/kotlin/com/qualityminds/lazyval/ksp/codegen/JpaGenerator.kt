package com.qualityminds.lazyval.ksp.codegen

import com.qualityminds.lazyval.ksp.spi.FilePerTypeGenerator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.SpiGenerator
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

// tag::docu[]
class JpaGenerator : FilePerTypeGenerator {

    companion object {
        private const val GENERATOR_ID = "jpa"
        private const val OPTION_GENERATED_PACKAGE = "lazyval.jpa.generatedPackage"
    }

    override fun generatorId(): String = GENERATOR_ID

    override fun requiredClasspath(): Collection<String> = listOf("jakarta.persistence.AttributeConverter")

    override fun supportedOptions(): Set<String> {
        return setOf(OPTION_GENERATED_PACKAGE)
    }

    override fun generateFilePerType(
        validatedElement: ValidatedKspGeneratorElement,
        userSettings: SpiGenerator.Settings
    ): GeneratorResult {
        // end::docu[]
        val element = validatedElement.element
        val lazyvalTypeName = element.toClassName()
        val wrappedType = validatedElement.wrappedProperty
        // KotlinPoet specific type-name
        val wrappedTypeName = wrappedType.type.toTypeName()

        val converterClassName = "${validatedElement.typeName}AttributeConverter"

        // Build convertToDatabaseColumn method - use the Kotlin accessor method name
        val convertToDatabaseColumn = FunSpec.builder("convertToDatabaseColumn")
            .addModifiers(KModifier.OVERRIDE)
            .returns(wrappedTypeName.copy(nullable = true))
            .addParameter("type", lazyvalTypeName.copy(nullable = true))
            .apply {
                addStatement("return type?.${validatedElement.kotlinAccessor}")
            }
            .build()

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

        val converterClass = TypeSpec.classBuilder(converterClassName)
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

        // Determine package
        val packageName = userSettings.options[OPTION_GENERATED_PACKAGE]
            ?: "${extractRootPackage(element)}.boundary.persistence"

        val fileSpec = FileSpec.builder(packageName, converterClassName).addType(converterClass).build()

        return GeneratorResult.Kotlin(
            GeneratorResult.Metadata(packageName, converterClassName),
            fileSpec.toString())

    }
}