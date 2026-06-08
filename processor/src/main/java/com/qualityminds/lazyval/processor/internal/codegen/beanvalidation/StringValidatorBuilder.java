package com.qualityminds.lazyval.processor.internal.codegen.beanvalidation;

import com.palantir.javapoet.*;
import com.qualityminds.lazyval.processor.internal.codegen.GeneratedStamp;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.Set;
import java.util.stream.Stream;

class StringValidatorBuilder {

    private static final Set<String> STRING_TYPES = Set.of("String");

    private static final ClassName CONSTRAINT_VALIDATOR = ClassName.get("jakarta.validation", "ConstraintValidator");
    private static final ClassName CONSTRAINT_VALIDATOR_CONTEXT = ClassName.get("jakarta.validation", "ConstraintValidatorContext");

    static boolean supports(String typeName) {
        return STRING_TYPES.contains(typeName);
    }

    static Stream<GeneratorResult> build(ValidatedGeneratorElement element, String packageName) {
        return Stream.concat(
                buildPatternValidator(element, packageName),
                buildEmailValidator(element, packageName));
    }

    private static Stream<GeneratorResult> buildPatternValidator(ValidatedGeneratorElement element, String packageName) {
        TypeMirror lazyvalType = element.element().asType();
        String className = element.typeName().name() + "PatternValidator";

        ClassName patternAnnotation = ClassName.get("jakarta.validation.constraints", "Pattern");

        TypeSpec validator = TypeSpec.classBuilder(className)
                .addAnnotation(GeneratedStamp.forGenerator(BeanValidationGenerator.class))
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ParameterizedTypeName.get(
                        CONSTRAINT_VALIDATOR, patternAnnotation, TypeName.get(lazyvalType)))
                .addField(FieldSpec.builder(String.class, "regex", Modifier.PRIVATE).build())
                .addMethod(buildInitialize(patternAnnotation, "this.regex = constraintAnnotation.regexp()"))
                .addMethod(buildIsValid(element, lazyvalType, "regex"))
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, validator).build();
        return ResultHelper.toResultStream(javaFile, packageName, className);
    }

    private static Stream<GeneratorResult> buildEmailValidator(ValidatedGeneratorElement element, String packageName) {
        TypeMirror lazyvalType = element.element().asType();
        String className = element.typeName().name() + "EmailValidator";

        ClassName emailAnnotation = ClassName.get("jakarta.validation.constraints", "Email");

        TypeSpec validator = TypeSpec.classBuilder(className)
                .addAnnotation(GeneratedStamp.forGenerator(BeanValidationGenerator.class))
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ParameterizedTypeName.get(
                        CONSTRAINT_VALIDATOR, emailAnnotation, TypeName.get(lazyvalType)))
                .addField(FieldSpec.builder(String.class, "regexp", Modifier.PRIVATE).build())
                .addMethod(buildInitialize(emailAnnotation, "this.regexp = constraintAnnotation.regexp()"))
                .addMethod(buildIsValid(element, lazyvalType, "regexp"))
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, validator).build();
        return ResultHelper.toResultStream(javaFile, packageName, className);
    }

    private static MethodSpec buildInitialize(ClassName annotationType, String initStatement) {
        return MethodSpec.methodBuilder("initialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(annotationType, "constraintAnnotation")
                .addStatement(initStatement)
                .build();
    }

    private static MethodSpec buildIsValid(ValidatedGeneratorElement element, TypeMirror lazyvalType, String fieldName) {
        return MethodSpec.methodBuilder("isValid")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addParameter(TypeName.get(lazyvalType), "value")
                .addParameter(CONSTRAINT_VALIDATOR_CONTEXT, "context")
                .beginControlFlow("if (value == null)")
                .addStatement("return true")
                .endControlFlow()
                .addStatement("return value.$L.matches($L)", element.accessor(), fieldName)
                .build();
    }
}
