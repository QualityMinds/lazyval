package com.qualityminds.lazyval.ksp.internal.codegen.beanvalidation

import com.qualityminds.lazyval.ksp.internal.codegen.GeneratedStamp.addGeneratedAnnotation
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import java.util.stream.Stream

internal object TemporalValidatorBuilder {

    private val TEMPORAL_TYPES = setOf(
        "LocalDate", "LocalDateTime", "LocalTime",
        "OffsetDateTime", "OffsetTime", "ZonedDateTime",
        "Instant", "Year", "YearMonth", "HijrahDate",
        "JapaneseDate", "MinguoDate", "ThaiBuddhistDate"
    )

    private val CONSTRAINT_VALIDATOR = ClassName("jakarta.validation", "ConstraintValidator")
    private val CONSTRAINT_VALIDATOR_CONTEXT = ClassName("jakarta.validation", "ConstraintValidatorContext")

    fun supports(typeName: String): Boolean = typeName in TEMPORAL_TYPES

    fun build(ctx: Generator.Context, element: ValidatedKspGeneratorElement, packageName: String): Stream<GeneratorResult> {
        return Stream.of(
            buildValidator(ctx, element, packageName, "Past"),
            buildValidator(ctx, element, packageName, "Future"),
            buildValidator(ctx, element, packageName, "PastOrPresent"),
            buildValidator(ctx, element, packageName, "FutureOrPresent")
        ).flatMap { it }
    }

    private fun buildValidator(ctx: Generator.Context, element: ValidatedKspGeneratorElement, packageName: String, annotationName: String): Stream<GeneratorResult> {
        val lazyvalTypeName = element.element.toClassName()
        val className = "${element.typeName.name}${annotationName}Validator"
        val temporalAnnotation = ClassName("jakarta.validation.constraints", annotationName)

        val typeSpec = TypeSpec.classBuilder(className)
            .addGeneratedAnnotation(BeanValidationGenerator::class, ctx)
            .addSuperinterface(
                CONSTRAINT_VALIDATOR.parameterizedBy(temporalAnnotation, lazyvalTypeName)
            )
            .addFunction(buildIsValid(element, lazyvalTypeName, annotationName))
            .build()

        val fileSpec = FileSpec.builder(packageName, className).addType(typeSpec).build()
        return ResultHelper.toResultStream(fileSpec, packageName, className)
    }

    private fun buildIsValid(element: ValidatedKspGeneratorElement, lazyvalTypeName: ClassName, annotationName: String): FunSpec {
        val wrappedTypeName = element.wrappedProperty.type.declaration.simpleName.asString()
        val nowExpression = resolveNowExpression(wrappedTypeName)

        val comparison = when (annotationName) {
            "Past" -> "return value.${element.kotlinAccessor}.isBefore($nowExpression)"
            "Future" -> "return value.${element.kotlinAccessor}.isAfter($nowExpression)"
            "PastOrPresent" -> "return !value.${element.kotlinAccessor}.isAfter($nowExpression)"
            "FutureOrPresent" -> "return !value.${element.kotlinAccessor}.isBefore($nowExpression)"
            else -> throw IllegalArgumentException("Unknown temporal annotation: $annotationName")
        }

        return FunSpec.builder("isValid")
            .addModifiers(KModifier.OVERRIDE)
            .returns(Boolean::class)
            .addParameter("value", lazyvalTypeName.copy(nullable = true))
            .addParameter("context", CONSTRAINT_VALIDATOR_CONTEXT)
            .beginControlFlow("if (value == null)")
            .addStatement("return true")
            .endControlFlow()
            .addStatement(comparison)
            .build()
    }

    private fun resolveNowExpression(wrappedTypeName: String): String {
        return when (wrappedTypeName) {
            "Instant" -> "java.time.Instant.now()"
            "LocalDate" -> "java.time.LocalDate.now()"
            "LocalDateTime" -> "java.time.LocalDateTime.now()"
            "LocalTime" -> "java.time.LocalTime.now()"
            "OffsetDateTime" -> "java.time.OffsetDateTime.now()"
            "OffsetTime" -> "java.time.OffsetTime.now()"
            "ZonedDateTime" -> "java.time.ZonedDateTime.now()"
            "Year" -> "java.time.Year.now()"
            "YearMonth" -> "java.time.YearMonth.now()"
            else -> "java.time.chrono.$wrappedTypeName.now()"
        }
    }
}
