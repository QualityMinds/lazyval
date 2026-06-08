package com.qualityminds.lazyval.processor.internal.codegen.beanvalidation;

import com.palantir.javapoet.*;
import com.qualityminds.lazyval.processor.internal.codegen.GeneratedStamp;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.Set;
import java.util.stream.Stream;

class TemporalValidatorBuilder {

    private static final Set<String> TEMPORAL_TYPES = Set.of(
            "LocalDate", "LocalDateTime", "LocalTime",
            "OffsetDateTime", "OffsetTime", "ZonedDateTime",
            "Instant", "Year", "YearMonth", "HijrahDate",
            "JapaneseDate", "MinguoDate", "ThaiBuddhistDate"
    );

    private static final ClassName CONSTRAINT_VALIDATOR = ClassName.get("jakarta.validation", "ConstraintValidator");
    private static final ClassName CONSTRAINT_VALIDATOR_CONTEXT = ClassName.get("jakarta.validation", "ConstraintValidatorContext");

    static boolean supports(String typeName) {
        return TEMPORAL_TYPES.contains(typeName);
    }

    static Stream<GeneratorResult> build(ValidatedGeneratorElement element, String packageName) {
        return Stream.of(
                buildValidator(element, packageName, "Past"),
                buildValidator(element, packageName, "Future"),
                buildValidator(element, packageName, "PastOrPresent"),
                buildValidator(element, packageName, "FutureOrPresent")
        ).flatMap(s -> s);
    }

    private static Stream<GeneratorResult> buildValidator(ValidatedGeneratorElement element, String packageName, String annotationName) {
        TypeMirror lazyvalType = element.element().asType();
        String className = element.typeName().name() + annotationName + "Validator";

        ClassName temporalAnnotation = ClassName.get("jakarta.validation.constraints", annotationName);

        TypeSpec validator = TypeSpec.classBuilder(className)
                .addAnnotation(GeneratedStamp.forGenerator(BeanValidationGenerator.class))
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ParameterizedTypeName.get(
                        CONSTRAINT_VALIDATOR, temporalAnnotation, TypeName.get(lazyvalType)))
                .addMethod(buildIsValid(element, lazyvalType, annotationName))
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, validator).build();
        return ResultHelper.toResultStream(javaFile, packageName, className);
    }

    private static MethodSpec buildIsValid(ValidatedGeneratorElement element, TypeMirror lazyvalType, String annotationName) {
        var builder = MethodSpec.methodBuilder("isValid")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addParameter(TypeName.get(lazyvalType), "value")
                .addParameter(CONSTRAINT_VALIDATOR_CONTEXT, "context")
                .beginControlFlow("if (value == null)")
                .addStatement("return true")
                .endControlFlow();

        String wrappedTypeName = element.wrappedType().typeName().simpleName();
        String nowExpression = resolveNowExpression(wrappedTypeName);

        if (annotationName.equals("Past") || annotationName.equals("Future")) {
            String comparison = annotationName.equals("Past") ? "isBefore" : "isAfter";
            builder.addStatement("return value.$L.$L($L)", element.accessor(), comparison, nowExpression);
        } else {
            String negatedMethod = annotationName.equals("PastOrPresent") ? "isAfter" : "isBefore";
            builder.addStatement("return !value.$L.$L($L)", element.accessor(), negatedMethod, nowExpression);
        }

        return builder.build();
    }

    private static String resolveNowExpression(String wrappedTypeName) {
        return switch (wrappedTypeName) {
            case "Instant" -> "java.time.Instant.now()";
            case "LocalDate" -> "java.time.LocalDate.now()";
            case "LocalDateTime" -> "java.time.LocalDateTime.now()";
            case "LocalTime" -> "java.time.LocalTime.now()";
            case "OffsetDateTime" -> "java.time.OffsetDateTime.now()";
            case "OffsetTime" -> "java.time.OffsetTime.now()";
            case "ZonedDateTime" -> "java.time.ZonedDateTime.now()";
            case "Year" -> "java.time.Year.now()";
            case "YearMonth" -> "java.time.YearMonth.now()";
            default -> "java.time.chrono." + wrappedTypeName + ".now()";
        };
    }
}
