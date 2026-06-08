package com.qualityminds.lazyval.ksp.internal.codegen.beanvalidation

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import java.util.stream.Stream

/**
 * Generates a `ConstraintValidator` for each constraint annotation supported by the wrapped
 * type (string patterns, numeric ranges, temporal constraints).
 *
 * ## Null invariants
 *
 * Following the Bean Validation specification, `isValid(value: DomainType?, context)` always
 * accepts a nullable `value`: `null` is considered valid and immediately returns `true`, leaving
 * enforcement of non-nullability to a separate `@NotNull` constraint on the field.
 *
 * The second type parameter of `ConstraintValidator<A, T>` is expressed as non-nullable (`T`,
 * not `T?`). The nullable `isValid` parameter is a fixed part of the Bean Validation contract,
 * independent of whether the domain type's factory method can return `null`.
 */
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
            .flatMap { generateValidators(context, it, packageName) }
    }

    private fun generateValidators(context: Generator.Context, element: ValidatedKspGeneratorElement, packageName: String): Stream<GeneratorResult> {
        val wrappedTypeName = element.wrappedProperty.type.declaration.simpleName.asString()

        return when {
            StringValidatorBuilder.supports(wrappedTypeName) -> StringValidatorBuilder.build(context, element, packageName)
            NumericValidatorBuilder.supports(wrappedTypeName) -> NumericValidatorBuilder.build(context, element, packageName)
            TemporalValidatorBuilder.supports(wrappedTypeName) -> TemporalValidatorBuilder.build(context, element, packageName)
            else -> Stream.empty()
        }
    }
}
