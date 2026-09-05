package com.qualityminds.lazyval.ksp.internal.codegen

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.internal.codegen.GeneratedStamp.addGeneratedAnnotation
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.StockGeneratorIds
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import java.util.stream.Stream

/**
 * Generates a JPA `AttributeConverter` for each domain-primitive.
 *
 * ## Null invariants
 *
 * Both `convertToDatabaseColumn` and `convertToEntityAttribute` use safe-call operators
 * (`?.`) to propagate `null` transparently: a `null` column value maps to a `null` entity
 * attribute, and a `null` entity attribute maps to a `null` column value.
 *
 * The generated converter always accepts and returns nullable types (`DomainType?` /
 * `InnerType?`) because JPA may pass `null` for optional columns regardless of whether the
 * domain type's factory method can return `null`. No distinction is made between nullable
 * and non-nullable factories; null is simply passed through in both directions.
 */
// tag::docu[]
class JpaGenerator : Generator {

    companion object {
        private const val OPTION_GENERATED_PACKAGE = "lazyval.jpa.package"
    }

    override fun generatorId(): String = StockGeneratorIds.JPA

    override fun requiredClasspath(): Set<String> = setOf("jakarta.persistence.AttributeConverter")

    override fun supportedOptions(): Set<String> {
        return setOf(OPTION_GENERATED_PACKAGE)
    }

    override fun generate(
        validatedElements: NonEmptySet<ValidatedKspGeneratorElement>,
        context: Generator.Context
    ): Stream<GeneratorResult> {
        // end::docu[]

        val packageName = context.generatorPackage(OPTION_GENERATED_PACKAGE, "boundary.persistence.jpa")

        return validatedElements.stream()
            .map { buildAttributeConverter(context, it) }
            .map { FileSpec.builder(packageName, it.name!!).addType(it).build() }
            .map { GeneratorResult.Kotlin(
                GeneratorResult.Metadata(it.packageName, it.name),
                it.toString()) }
    }


    private fun buildAttributeConverter(context: Generator.Context, validatedElement: ValidatedKspGeneratorElement): TypeSpec {
        val element = validatedElement.element
        val lazyvalTypeName = element.toClassName()
        // The unwrapped payload: a value class has no runtime form of its own, so the column type is
        // whatever it erases to.
        val payloadTypeName = validatedElement.payloadType.toTypeName()

        val converterClassName = "${validatedElement.name.flatName()}AttributeConverter"

        val convertToDatabaseColumn = buildConvertToDatabaseColumn(lazyvalTypeName, payloadTypeName, validatedElement)
        val convertToEntityAttribute = buildConvertToEntityAttribute(lazyvalTypeName, payloadTypeName, validatedElement)

        return TypeSpec.classBuilder(converterClassName)
            .addGeneratedAnnotation(JpaGenerator::class, context)
            .addAnnotation(
                AnnotationSpec.builder(ClassName("jakarta.persistence", "Converter"))
                    .addMember("autoApply = true")
                    .build()
            )
            .addSuperinterface(
                ClassName("jakarta.persistence", "AttributeConverter")
                    .parameterizedBy(
                        lazyvalTypeName.copy(nullable = true),
                        payloadTypeName.copy(nullable = true)
                    )
            )
            .addFunction(convertToDatabaseColumn)
            .addFunction(convertToEntityAttribute)
            .build()
    }

    private fun buildConvertToDatabaseColumn(
        lazyvalTypeName: ClassName,
        payloadTypeName: TypeName,
        validatedElement: ValidatedKspGeneratorElement
    ): FunSpec {
        val convertToDatabaseColumn = FunSpec.builder("convertToDatabaseColumn")
            .addModifiers(KModifier.OVERRIDE)
            .returns(payloadTypeName.copy(nullable = true))
            .addParameter("type", lazyvalTypeName.copy(nullable = true))
            .apply {
                addStatement("return ${validatedElement.kotlin.readOrNull("type")}")
            }
            .build()
        return convertToDatabaseColumn
    }

    private fun buildConvertToEntityAttribute(
        lazyvalTypeName: ClassName,
        payloadTypeName: TypeName,
        validatedElement: ValidatedKspGeneratorElement
    ): FunSpec {
        val parameterName = "dbValue"
        // Build convertToEntityAttribute method
        val convertToEntityAttribute = FunSpec.builder("convertToEntityAttribute")
            .addModifiers(KModifier.OVERRIDE)
            .returns(lazyvalTypeName.copy(nullable = true))
            .addParameter(parameterName, payloadTypeName.copy(nullable = true))
            .apply {
                addStatement("return ${validatedElement.kotlin.createOrNull(parameterName)}")
            }
            .build()
        return convertToEntityAttribute
    }
}