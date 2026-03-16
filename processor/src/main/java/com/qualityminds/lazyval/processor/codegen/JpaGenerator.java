package com.qualityminds.lazyval.processor.codegen;

import com.palantir.javapoet.*;
import com.qualityminds.lazyval.processor.spi.FilePerTypeGenerator;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.Collection;
import java.util.List;
import java.util.Set;

// must only be public for ServiceLoader, but it is not part of the API
@SuppressWarnings("doclint:accessibility,missing")
// tag::docu[]
public class JpaGenerator implements FilePerTypeGenerator {

    private static final String GENERATOR_ID = "jpa";
    private static final String OPTION_GENERATED_PACKAGE = "lazyval.jpa.generatedPackage";

    @Override
    public String generatorId() {
        return GENERATOR_ID;
    }

    @Override
    public Collection<String> requiredClasspath() {
        return List.of("jakarta.persistence.AttributeConverter");
    }

    @Override
    public Set<String> supportedOptions() {
        return Set.of(OPTION_GENERATED_PACKAGE);
    }

    @Override
    public GeneratorResult generateFilePerType(ValidatedGeneratorElement validElement, Settings userSettings){
        // end::docu[]
        TypeMirror type = validElement.element().asType();
        var wrappedType = validElement.wrappedType();
        TypeName wrappedTypeName;
        if (wrappedType.isPrimitive()) {
            // Box primitive types for JPA generics
            wrappedTypeName = TypeName.get(wrappedType.typeMirror()).box();
        } else {
            wrappedTypeName = TypeName.get(wrappedType.typeMirror());
        }

        var parameterName = "typeMirror";
        // Jpa Converter needs to check for null due to boxing primitive types
        final MethodSpec convertToDatabaseColumn = MethodSpec.methodBuilder("convertToDatabaseColumn")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(wrappedTypeName)
                    .addParameter(TypeName.get(type), parameterName)
                    .beginControlFlow("if(%s == null)".formatted(parameterName))
                    .addStatement("return null")
                    .endControlFlow()
                    .addStatement(String.format("return %s.%s", parameterName, validElement.accessor()))
                    .build();

        parameterName = "dbValue";
        final MethodSpec convertToEntityAttribute = MethodSpec.methodBuilder("convertToEntityAttribute")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(TypeName.get(type))
                    .addParameter(wrappedTypeName, parameterName)
                    .beginControlFlow("if(%s == null)".formatted(parameterName))
                    .addStatement("return null")
                    .endControlFlow()
                    .addStatement("return $L", validElement.objectCreation(parameterName))
                    .build();

        TypeSpec jpaConverter = TypeSpec.classBuilder(validElement.typeName() + "AttributeConverter")
                .addAnnotation(AnnotationSpec.builder(ClassName.get("jakarta.persistence", "Converter"))
                        .addMember("autoApply", "$L", "true")
                        .build())
                .addSuperinterface(ParameterizedTypeName.get(
                        ClassName.get("jakarta.persistence", "AttributeConverter"),
                        TypeName.get(type),
                        wrappedTypeName))
                .addModifiers(Modifier.PUBLIC)
                .addMethod(convertToDatabaseColumn)
                .addMethod(convertToEntityAttribute)
                .build();

        String jpaConverterPackage = userSettings.get(OPTION_GENERATED_PACKAGE)
                .orElse(String.format("%s.boundary.persistence", extractRootPackage(validElement.element())));
        if(jpaConverterPackage.charAt(0) == '.'){
            jpaConverterPackage = jpaConverterPackage.substring(1);
        }
        final JavaFile javaFile = JavaFile.builder(jpaConverterPackage, jpaConverter).build();
        return new GeneratorResult.Java(
                new GeneratorResult.Metadata(javaFile.packageName(), javaFile.typeSpec().name()),
                javaFile.toString());
    }
}
