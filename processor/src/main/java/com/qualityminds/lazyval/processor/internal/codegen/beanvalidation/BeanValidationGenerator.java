package com.qualityminds.lazyval.processor.internal.codegen.beanvalidation;

import com.qualityminds.lazyval.collections.NonEmptySet;
import com.qualityminds.lazyval.processor.spi.Generator;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Generates a {@code ConstraintValidator} for each constraint annotation supported by the
 * wrapped type (string patterns, numeric ranges, temporal constraints).
 *
 * <h3>Null invariants</h3>
 * Following the Bean Validation specification, {@code isValid(value, context)} always accepts
 * a nullable {@code value}: {@code null} is considered valid and immediately returns
 * {@code true}, leaving enforcement of non-nullability to a separate {@code @NotNull}
 * constraint on the field.
 * The second type parameter of {@code ConstraintValidator<A, T>} is the validated domain type
 * (non-null concept). The nullable {@code isValid} parameter is a fixed part of the Bean
 * Validation contract, independent of whether the domain type's factory method can return
 * {@code null}.
 */
public class BeanValidationGenerator implements Generator {

    private static final String GENERATOR_ID = "beanvalidation";
    private static final String OPTION_GENERATED_PACKAGE = "lazyval.beanvalidation.package";

    @Override
    public String generatorId() {
        return GENERATOR_ID;
    }

    @Override
    public Collection<String> requiredClasspath() {
        return List.of("jakarta.validation.ConstraintValidator");
    }

    @Override
    public Set<String> supportedOptions() {
        return Set.of(OPTION_GENERATED_PACKAGE);
    }

    @Override
    public Stream<GeneratorResult> generate(NonEmptySet<ValidatedGeneratorElement> elements, Context context) {
        final String packageName = context.generatorPackage(OPTION_GENERATED_PACKAGE, null);

        return elements.stream()
                .flatMap(element -> generateValidators(element, packageName));
    }

    private Stream<GeneratorResult> generateValidators(ValidatedGeneratorElement element, String packageName) {
        String wrappedTypeName = element.wrappedType().typeName().simpleName();

        if (StringValidatorBuilder.supports(wrappedTypeName)) {
            return StringValidatorBuilder.build(element, packageName);
        } else if (NumericValidatorBuilder.supports(wrappedTypeName)) {
            return NumericValidatorBuilder.build(element, packageName);
        } else if (TemporalValidatorBuilder.supports(wrappedTypeName)) {
            return TemporalValidatorBuilder.build(element, packageName);
        }
        return Stream.empty();
    }
}
