package com.qualityminds.lazyval.processor.internal.codegen.beanvalidation;

import com.qualityminds.lazyval.collections.NonEmptySet;
import com.qualityminds.lazyval.processor.spi.Generator;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

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
