package com.qualityminds.lazyval.ksp.internal.codegen.beanvalidation

import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import java.util.stream.Stream

internal object StringValidatorBuilder {

    private val STRING_TYPES = setOf("String")

    private val CONSTRAINT_VALIDATOR = ClassName("jakarta.validation", "ConstraintValidator")
    private val CONSTRAINT_VALIDATOR_CONTEXT = ClassName("jakarta.validation", "ConstraintValidatorContext")

    fun supports(typeName: String): Boolean = typeName in STRING_TYPES

    fun build(element: ValidatedKspGeneratorElement, packageName: String): Stream<GeneratorResult> {
        return Stream.concat(
            buildPatternValidator(element, packageName),
            buildEmailValidator(element, packageName))
    }

    private fun buildPatternValidator(element: ValidatedKspGeneratorElement, packageName: String): Stream<GeneratorResult> {
        val lazyvalTypeName = element.element.toClassName()
        val className = "${element.typeName.name}PatternValidator"
        val patternAnnotation = ClassName("jakarta.validation.constraints", "Pattern")

        val typeSpec = TypeSpec.classBuilder(className)
            .addSuperinterface(
                CONSTRAINT_VALIDATOR.parameterizedBy(patternAnnotation, lazyvalTypeName)
            )
            .addProperty(PropertySpec.builder("regex", String::class, KModifier.PRIVATE)
                .mutable(true)
                .initializer("%S", "")
                .build())
            .addFunction(buildInitialize(patternAnnotation, "this.regex = constraintAnnotation.regexp"))
            .addFunction(buildIsValid(element, lazyvalTypeName, "regex"))
            .build()

        val fileSpec = FileSpec.builder(packageName, className).addType(typeSpec).build()
        return ResultHelper.toResultStream(fileSpec, packageName, className)
    }

    private fun buildEmailValidator(element: ValidatedKspGeneratorElement, packageName: String): Stream<GeneratorResult> {
        val lazyvalTypeName = element.element.toClassName()
        val className = "${element.typeName.name}EmailValidator"
        val emailAnnotation = ClassName("jakarta.validation.constraints", "Email")

        val typeSpec = TypeSpec.classBuilder(className)
            .addSuperinterface(
                CONSTRAINT_VALIDATOR.parameterizedBy(emailAnnotation, lazyvalTypeName)
            )
            .addProperty(PropertySpec.builder("regexp", String::class, KModifier.PRIVATE)
                .mutable(true)
                .initializer("%S", "")
                .build())
            .addFunction(buildInitialize(emailAnnotation, "this.regexp = constraintAnnotation.regexp"))
            .addFunction(buildIsValid(element, lazyvalTypeName, "regexp"))
            .build()

        val fileSpec = FileSpec.builder(packageName, className).addType(typeSpec).build()
        return ResultHelper.toResultStream(fileSpec, packageName, className)
    }

    private fun buildInitialize(annotationType: ClassName, initStatement: String): FunSpec {
        return FunSpec.builder("initialize")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("constraintAnnotation", annotationType)
            .addStatement(initStatement)
            .build()
    }

    private fun buildIsValid(element: ValidatedKspGeneratorElement, lazyvalTypeName: ClassName, fieldName: String): FunSpec {
        return FunSpec.builder("isValid")
            .addModifiers(KModifier.OVERRIDE)
            .returns(Boolean::class)
            .addParameter("value", lazyvalTypeName.copy(nullable = true))
            .addParameter("context", CONSTRAINT_VALIDATOR_CONTEXT)
            .beginControlFlow("if (value == null)")
            .addStatement("return true")
            .endControlFlow()
            .addStatement("return value.${element.kotlinAccessor}.matches(Regex($fieldName))")
            .build()
    }
}
