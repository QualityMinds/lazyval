package com.qualityminds.lazyval.ksp.internal.codegen.beanvalidation

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import java.util.stream.Stream

class BeanValidationGenerator : Generator {

    companion object {
        private const val GENERATOR_ID = "beanvalidation"
        private const val OPTION_GENERATED_PACKAGE = "lazyval.beanvalidation.package"
    }

    override fun generatorId(): String = GENERATOR_ID

    override fun requiredClasspath(): Collection<String> = listOf("jakarta.validation.ConstraintValidator")

    override fun supportedOptions(): Set<String> = setOf(OPTION_GENERATED_PACKAGE)

    override fun generate(
        validatedElements: NonEmptySet<ValidatedKspGeneratorElement>,
        context: Generator.Context
    ): Stream<GeneratorResult> {
        val packageName = context.generatorPackage(OPTION_GENERATED_PACKAGE, null)

        return validatedElements.stream()
            .flatMap { generateValidators(it, packageName) }
    }

    private fun generateValidators(element: ValidatedKspGeneratorElement, packageName: String): Stream<GeneratorResult> {
        val wrappedTypeName = element.wrappedProperty.type.declaration.simpleName.asString()

        return when {
            StringValidatorBuilder.supports(wrappedTypeName) -> StringValidatorBuilder.build(element, packageName)
            NumericValidatorBuilder.supports(wrappedTypeName) -> NumericValidatorBuilder.build(element, packageName)
            TemporalValidatorBuilder.supports(wrappedTypeName) -> TemporalValidatorBuilder.build(element, packageName)
            else -> Stream.empty()
        }
    }
}
