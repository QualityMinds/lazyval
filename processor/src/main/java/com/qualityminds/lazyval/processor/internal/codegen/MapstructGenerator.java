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
@SuppressWarnings("doclint:accessibility,missing")
final public class MapstructGenerator implements Generator {

    private static final String GENERATOR_ID = "mapstruct";
    private static final String OPTION_GENERATED_PACKAGE = "lazyval.mapstruct.package";

    @Override
    public String generatorId() {
        return GENERATOR_ID;
    }

    @Override
    public Collection<String> requiredClasspath() {
        return List.of("org.mapstruct.Mapper");
    }

    @Override
    public Set<String> supportedOptions() {
        return Set.of(OPTION_GENERATED_PACKAGE);
    }

    @Override
    public Stream<GeneratorResult> generate(NonEmptySet<ValidatedGeneratorElement> elements, Context context){
        var mapperAnnotationBuilder = AnnotationSpec.builder(ClassName.get("org.mapstruct", "Mapper"))
                .addMember("unmappedTargetPolicy", "$L", "org.mapstruct.ReportingPolicy.ERROR");

        final String mapperPackage = context.generatorPackage(OPTION_GENERATED_PACKAGE, null);

        TypeSpec.Builder typeSpecBuilder = TypeSpec.interfaceBuilder("LazyvalMapper")
                .addAnnotation(mapperAnnotationBuilder.build())
                .addModifiers(Modifier.PUBLIC);

        elements.forEach(validElement -> {
            typeSpecBuilder.addMethod(buildMapToWrappedType(validElement));
            typeSpecBuilder.addMethod(buildMapType(validElement));
        });

        JavaFile javaFile = JavaFile.builder(mapperPackage, typeSpecBuilder.build()).build();
        return Stream.of(new GeneratorResult.Java(
                new GeneratorResult.Metadata(javaFile.packageName(), javaFile.typeSpec().name()),
                javaFile.toString()));
    }

    private static MethodSpec buildMapType(ValidatedGeneratorElement validElement) {
        TypeMirror type = validElement.element().asType();
        var wrappedType = validElement.wrappedType();
        String parameterName;
        parameterName = "value";
        final MethodSpec map;
        if(wrappedType.isPrimitive()){
            map = MethodSpec.methodBuilder(String.format("map%s", validElement.typeName()))
                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                    .returns(TypeName.get(type))
                    .addParameter(TypeName.get(wrappedType.typeMirror()), parameterName)
                    .addStatement("return $L", validElement.objectCreation(parameterName))
                    .build();
        } else {
            map = MethodSpec.methodBuilder(String.format("map%s", validElement.typeName()))
                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                    .returns(TypeName.get(type))
                    .addParameter(TypeName.get(wrappedType.typeMirror()), parameterName)
                    .beginControlFlow("if(%s == null)".formatted(parameterName))
                    .addStatement("return null")
                    .endControlFlow()
                    .addStatement("return $L", validElement.objectCreation(parameterName))
                    .build();
        }
        return map;
    }

    private static MethodSpec buildMapToWrappedType(ValidatedGeneratorElement validElement) {
        TypeMirror type = validElement.element().asType();
        var wrappedType = validElement.wrappedType();

        final MethodSpec mapToWrappedType;
        var parameterName = "type";
        if(wrappedType.isPrimitive()) {
            mapToWrappedType = MethodSpec.methodBuilder(String.format("map%sTo%s", validElement.typeName(), wrappedType.typeNameUpper()))
                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                    .returns(TypeName.get(wrappedType.typeMirror()))
                    .addParameter(TypeName.get(type), parameterName)
                    .addStatement(String.format("return %s.%s", parameterName, validElement.accessor()))
                    .build();
        } else {
            mapToWrappedType = MethodSpec.methodBuilder(String.format("map%sTo%s", validElement.typeName(), wrappedType.typeName()))
                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                    .returns(TypeName.get(wrappedType.typeMirror()))
                    .addParameter(TypeName.get(type), parameterName)
                    .beginControlFlow("if(%s == null)".formatted(parameterName))
                    .addStatement("return null")
                    .endControlFlow()
                    .addStatement(String.format("return %s.%s", parameterName, validElement.accessor()))
                    .build();
        }
        return mapToWrappedType;
    }
}
