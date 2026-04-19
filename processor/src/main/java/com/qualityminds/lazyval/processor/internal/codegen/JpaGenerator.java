package com.qualityminds.lazyval.processor.internal.codegen;

import com.palantir.javapoet.*;
import com.qualityminds.lazyval.collections.NonEmptySet;
import com.qualityminds.lazyval.processor.spi.Generator;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

// must only be public for ServiceLoader, but it is not part of the API
// tag::docu[]
public class JpaGenerator implements Generator {

    private static final String GENERATOR_ID = "jpa";
    private static final String OPTION_GENERATED_PACKAGE = "lazyval.jpa.package";

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
    public Stream<GeneratorResult> generate(NonEmptySet<ValidatedGeneratorElement> elements, Context context){
        // end::docu[]

        final String jpaConverterPackage = context.generatorPackage("boundary.persistence", OPTION_GENERATED_PACKAGE);

        return elements.stream().map(JpaGenerator::buildAttributeConverter)
                .map(attributeConverterSpec -> JavaFile.builder(jpaConverterPackage, attributeConverterSpec).build())
                .map(javaFile -> new GeneratorResult.Java(new GeneratorResult.Metadata(
                        javaFile.packageName(), javaFile.typeSpec().name()),
                        javaFile.toString()));
    }

    private static TypeSpec buildAttributeConverter(ValidatedGeneratorElement validElement) {
        TypeMirror type = validElement.element().asType();
        var wrappedType = validElement.wrappedType();
        TypeName wrappedTypeName;
        if (wrappedType.isPrimitive()) {
            // Box primitive types for JPA generics
            wrappedTypeName = TypeName.get(wrappedType.typeMirror()).box();
        } else {
            wrappedTypeName = TypeName.get(wrappedType.typeMirror());
        }

        return TypeSpec.classBuilder(validElement.typeName() + "AttributeConverter")
                .addAnnotation(AnnotationSpec.builder(ClassName.get("jakarta.persistence", "Converter"))
                        .addMember("autoApply", "$L", "true")
                        .build())
                .addSuperinterface(ParameterizedTypeName.get(
                        ClassName.get("jakarta.persistence", "AttributeConverter"),
                        TypeName.get(type),
                        wrappedTypeName))
                .addModifiers(Modifier.PUBLIC)
                .addMethod(buildConvertToDatabaseColumn(validElement, wrappedTypeName, type))
                .addMethod(buildConvertToEntityAttribute(validElement, type, wrappedTypeName))
                .build();
    }

    private static MethodSpec buildConvertToEntityAttribute(ValidatedGeneratorElement validElement, TypeMirror type, TypeName wrappedTypeName) {
        String parameterName = "dbValue";
        return MethodSpec.methodBuilder("convertToEntityAttribute")
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.get(type))
                .addParameter(wrappedTypeName, parameterName)
                .beginControlFlow("if(%s == null)".formatted(parameterName))
                .addStatement("return null")
                .endControlFlow()
                .addStatement("return $L", validElement.objectCreation(parameterName))
                .build();
    }

    private static MethodSpec buildConvertToDatabaseColumn(ValidatedGeneratorElement validElement, TypeName wrappedTypeName, TypeMirror type) {
        String parameterName = "type";
        // Jpa Converter needs to check for null due to boxing primitive types
        return MethodSpec.methodBuilder("convertToDatabaseColumn")
                .addModifiers(Modifier.PUBLIC)
                .returns(wrappedTypeName)
                .addParameter(TypeName.get(type), parameterName)
                .beginControlFlow("if(%s == null)".formatted(parameterName))
                .addStatement("return null")
                .endControlFlow()
                .addStatement(String.format("return %s.%s", parameterName, validElement.accessor()))
                .build();
    }
}
