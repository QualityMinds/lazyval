package com.qualityminds.lazyval.processor.internal.codegen.beanvalidation;

import com.palantir.javapoet.*;
import com.qualityminds.lazyval.processor.internal.codegen.GeneratedStamp;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.Set;
import java.util.stream.Stream;

class NumericValidatorBuilder {

    private static final Set<String> NUMERIC_TYPES = Set.of(
            "int", "Integer", "long", "Long", "short", "Short", "byte", "Byte",
            "float", "Float", "double", "Double", "BigDecimal", "BigInteger"
    );

    private static final ClassName CONSTRAINT_VALIDATOR = ClassName.get("jakarta.validation", "ConstraintValidator");
    private static final ClassName CONSTRAINT_VALIDATOR_CONTEXT = ClassName.get("jakarta.validation", "ConstraintValidatorContext");

    static boolean supports(String typeName) {
        return NUMERIC_TYPES.contains(typeName);
    }

    static Stream<GeneratorResult> build(ValidatedGeneratorElement element, String packageName) {
        return Stream.of(
                buildMinValidator(element, packageName),
                buildMaxValidator(element, packageName)
        ).flatMap(s -> s);
    }

    private static Stream<GeneratorResult> buildMinValidator(ValidatedGeneratorElement element, String packageName) {
        TypeMirror lazyvalType = element.element().asType();
        String className = element.typeName().name() + "MinValidator";

        ClassName minAnnotation = ClassName.get("jakarta.validation.constraints", "Min");

        TypeSpec validator = TypeSpec.classBuilder(className)
                .addAnnotation(GeneratedStamp.forGenerator(BeanValidationGenerator.class))
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ParameterizedTypeName.get(
                        CONSTRAINT_VALIDATOR, minAnnotation, TypeName.get(lazyvalType)))
                .addField(FieldSpec.builder(long.class, "min", Modifier.PRIVATE).build())
                .addMethod(buildMinInitialize(minAnnotation))
                .addMethod(buildMinIsValid(element, lazyvalType))
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, validator).build();
        return ResultHelper.toResultStream(javaFile, packageName, className);
    }

    private static MethodSpec buildMinInitialize(ClassName minAnnotation) {
        return MethodSpec.methodBuilder("initialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(minAnnotation, "constraintAnnotation")
                .addStatement("this.min = constraintAnnotation.value()")
                .build();
    }

    private static MethodSpec buildMinIsValid(ValidatedGeneratorElement element, TypeMirror lazyvalType) {
        return MethodSpec.methodBuilder("isValid")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addParameter(TypeName.get(lazyvalType), "value")
                .addParameter(CONSTRAINT_VALIDATOR_CONTEXT, "context")
                .beginControlFlow("if (value == null)")
                .addStatement("return true")
                .endControlFlow()
                .addStatement("return value.$L >= min", element.accessor())
                .build();
    }

    private static Stream<GeneratorResult> buildMaxValidator(ValidatedGeneratorElement element, String packageName) {
        TypeMirror lazyvalType = element.element().asType();
        String className = element.typeName().name() + "MaxValidator";

        ClassName maxAnnotation = ClassName.get("jakarta.validation.constraints", "Max");

        TypeSpec validator = TypeSpec.classBuilder(className)
                .addAnnotation(GeneratedStamp.forGenerator(BeanValidationGenerator.class))
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ParameterizedTypeName.get(
                        CONSTRAINT_VALIDATOR, maxAnnotation, TypeName.get(lazyvalType)))
                .addField(FieldSpec.builder(long.class, "max", Modifier.PRIVATE).build())
                .addMethod(buildMaxInitialize(maxAnnotation))
                .addMethod(buildMaxIsValid(element, lazyvalType))
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, validator).build();
        return ResultHelper.toResultStream(javaFile, packageName, className);
    }

    private static MethodSpec buildMaxInitialize(ClassName maxAnnotation) {
        return MethodSpec.methodBuilder("initialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(maxAnnotation, "constraintAnnotation")
                .addStatement("this.max = constraintAnnotation.value()")
                .build();
    }

    private static MethodSpec buildMaxIsValid(ValidatedGeneratorElement element, TypeMirror lazyvalType) {
        return MethodSpec.methodBuilder("isValid")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addParameter(TypeName.get(lazyvalType), "value")
                .addParameter(CONSTRAINT_VALIDATOR_CONTEXT, "context")
                .beginControlFlow("if (value == null)")
                .addStatement("return true")
                .endControlFlow()
                .addStatement("return value.$L <= max", element.accessor())
                .build();
    }
}
