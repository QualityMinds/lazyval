package de.qualityminds.lazyval.ksp.codegen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import de.qualityminds.lazyval.ksp.ValidatedKspGeneratorElement
import de.qualityminds.lazyval.ksp.spi.FilePerTypeGenerator
import de.qualityminds.lazyval.ksp.spi.GeneratorResult
import de.qualityminds.lazyval.ksp.spi.SpiGenerator

// tag::docu[]
class JpaGenerator : FilePerTypeGenerator {

    companion object {
        const val OPTION_GENERATED_PACKAGE = "lazyval.jpa.generatedPackage"
    }

    override fun generatorId(): String = "jpa"

    override fun requiredClasspath(): Collection<String> = listOf("jakarta.persistence.AttributeConverter")

    override fun generateFilePerType(
        validatedElement: ValidatedKspGeneratorElement,
        userSettings: SpiGenerator.Settings
    ): GeneratorResult {
        // end::docu[]
        val element = validatedElement.element
        val wrappedType = validatedElement.wrappedType
        val lazyvalTypeName = element.toClassName()

        // Handle primitive boxing for JPA generics
        val wrappedTypeName = if (wrappedType.isBoxedPrimitive() || wrappedType.isPrimitive()) {
            wrappedType.toTypeName().copy(nullable = false)
        } else {
            wrappedType.toTypeName()
        }

        val converterClassName = "${element.simpleName.asString()}AttributeConverter"

        // Build convertToDatabaseColumn method - use the Kotlin accessor method name
        val convertToDatabaseColumn = FunSpec.builder("convertToDatabaseColumn")
            .addModifiers(KModifier.OVERRIDE)
            .returns(wrappedTypeName.copy(nullable = true))
            .addParameter("type", lazyvalTypeName.copy(nullable = true))
            .apply {
                addStatement("return type?.${validatedElement.kotlinAccessorMethodName}")
            }
            .build()

        // Store factory method in local variable to avoid smart cast issues
        val factoryMethod = validatedElement.factoryMethod
        val objectCreation = if (factoryMethod != null) {
            "${lazyvalTypeName.simpleName}.${factoryMethod.simpleName.asString()}(dbValue)"
        } else {
            "${lazyvalTypeName.simpleName}(dbValue)"
        }

        // Build convertToEntityAttribute method
        val convertToEntityAttribute = FunSpec.builder("convertToEntityAttribute")
            .addModifiers(KModifier.OVERRIDE)
            .returns(lazyvalTypeName.copy(nullable = true))
            .addParameter("dbValue", wrappedTypeName.copy(nullable = true))
            .apply {
                addStatement("return dbValue?.let { $objectCreation }")
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