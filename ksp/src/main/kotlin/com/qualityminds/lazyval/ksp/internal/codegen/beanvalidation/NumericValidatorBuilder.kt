package com.qualityminds.lazyval.ksp.internal.codegen.beanvalidation

import com.qualityminds.lazyval.ksp.internal.codegen.GeneratedStamp.addGeneratedAnnotation
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import java.util.stream.Stream

internal object NumericValidatorBuilder {

    private val NUMERIC_TYPES = setOf(
        "Int", "Long", "Short", "Byte", "Float", "Double",
        "Integer", "BigDecimal", "BigInteger"
    )

    private val CONSTRAINT_VALIDATOR = ClassName("jakarta.validation", "ConstraintValidator")
    private val CONSTRAINT_VALIDATOR_CONTEXT = ClassName("jakarta.validation", "ConstraintValidatorContext")

    fun supports(typeName: String): Boolean = typeName in NUMERIC_TYPES

    fun build(ctx: Generator.Context, element: ValidatedKspGeneratorElement, packageName: String): Stream<GeneratorResult> {
        return Stream.concat(
            buildMinValidator(ctx, element, packageName),
            buildMaxValidator(ctx, element, packageName)
        )
    }

    private fun buildMinValidator(ctx: Generator.Context, element: ValidatedKspGeneratorElement, packageName: String): Stream<GeneratorResult> {
        val lazyvalTypeName = element.element.toClassName()
        val className = "${element.typeName.name}MinValidator"
        val minAnnotation = ClassName("jakarta.validation.constraints", "Min")

        val typeSpec = TypeSpec.classBuilder(className)
            .addGeneratedAnnotation(BeanValidationGenerator::class, ctx)
            .addSuperinterface(
                CONSTRAINT_VALIDATOR.parameterizedBy(minAnnotation, lazyvalTypeName)
            )
            .addProperty(PropertySpec.builder("min", Long::class, KModifier.PRIVATE)
                .mutable(true)
                .initializer("0L")
                .build())
            .addFunction(buildMinInitialize(minAnnotation))
            .addFunction(buildMinIsValid(element, lazyvalTypeName))
            .build()

        val fileSpec = FileSpec.builder(packageName, className).addType(typeSpec).build()
        return ResultHelper.toResultStream(fileSpec, packageName, className)
    }

    private fun buildMinInitialize(minAnnotation: ClassName): FunSpec {
        return FunSpec.builder("initialize")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("constraintAnnotation", minAnnotation)
            .addStatement("this.min = constraintAnnotation.value")
            .build()
    }

    private fun buildMinIsValid(element: ValidatedKspGeneratorElement, lazyvalTypeName: ClassName): FunSpec {
        return FunSpec.builder("isValid")
            .addModifiers(KModifier.OVERRIDE)
            .returns(Boolean::class)
            .addParameter("value", lazyvalTypeName.copy(nullable = true))
            .addParameter("context", CONSTRAINT_VALIDATOR_CONTEXT)
            .beginControlFlow("if (value == null)")
            .addStatement("return true")
            .endControlFlow()
            .addStatement("return value.${element.kotlinAccessor} >= min")
            .build()
    }

    private fun buildMaxValidator(ctx: Generator.Context, element: ValidatedKspGeneratorElement, packageName: String): Stream<GeneratorResult> {
        val lazyvalTypeName = element.element.toClassName()
        val className = "${element.typeName.name}MaxValidator"
        val maxAnnotation = ClassName("jakarta.validation.constraints", "Max")

        val typeSpec = TypeSpec.classBuilder(className)
            .addGeneratedAnnotation(BeanValidationGenerator::class, ctx)
            .addSuperinterface(
                CONSTRAINT_VALIDATOR.parameterizedBy(maxAnnotation, lazyvalTypeName)
            )
            .addProperty(PropertySpec.builder("max", Long::class, KModifier.PRIVATE)
                .mutable(true)
                .initializer("0L")
                .build())
            .addFunction(buildMaxInitialize(maxAnnotation))
            .addFunction(buildMaxIsValid(element, lazyvalTypeName))
            .build()

        val fileSpec = FileSpec.builder(packageName, className).addType(typeSpec).build()
        return ResultHelper.toResultStream(fileSpec, packageName, className)
    }

    private fun buildMaxInitialize(maxAnnotation: ClassName): FunSpec {
        return FunSpec.builder("initialize")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("constraintAnnotation", maxAnnotation)
            .addStatement("this.max = constraintAnnotation.value")
            .build()
    }

    private fun buildMaxIsValid(element: ValidatedKspGeneratorElement, lazyvalTypeName: ClassName): FunSpec {
        return FunSpec.builder("isValid")
            .addModifiers(KModifier.OVERRIDE)
            .returns(Boolean::class)
            .addParameter("value", lazyvalTypeName.copy(nullable = true))
            .addParameter("context", CONSTRAINT_VALIDATOR_CONTEXT)
            .beginControlFlow("if (value == null)")
            .addStatement("return true")
            .endControlFlow()
            .addStatement("return value.${element.kotlinAccessor} <= max")
            .build()
    }
}
