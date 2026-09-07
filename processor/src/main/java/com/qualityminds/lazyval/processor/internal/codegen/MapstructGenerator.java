package com.qualityminds.lazyval.processor.internal.codegen;

import com.palantir.javapoet.*;
import com.qualityminds.lazyval.collections.NonEmptySet;
import com.qualityminds.lazyval.processor.spi.Generator;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;
import com.qualityminds.lazyval.processor.spi.StockGeneratorIds;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.Modifier;
import java.util.Set;
import java.util.stream.Stream;
import static com.qualityminds.lazyval.processor.internal.codegen.JavaPoetExprs.code;

// must only be public for ServiceLoader, but it is not part of the API
@SuppressWarnings("doclint:accessibility,missing")
final public class MapstructGenerator implements Generator {

    private static final String OPTION_GENERATED_PACKAGE = "lazyval.mapstruct.package";

    @Override
    public String generatorId() {
        return StockGeneratorIds.MAPSTRUCT;
    }

    @Override
    public Set<String> requiredClasspath() {
        return Set.of("org.mapstruct.Mapper");
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
                .addAnnotation(GeneratedStamp.forGenerator(MapstructGenerator.class))
                .addAnnotation(mapperAnnotationBuilder.build())
                .addModifiers(Modifier.PUBLIC);

        elements.forEach(validElement -> {
            typeSpecBuilder.addMethod(buildMapToPayload(validElement));
            typeSpecBuilder.addMethod(buildMapType(validElement));
        });

        JavaFile javaFile = JavaFile.builder(mapperPackage, typeSpecBuilder.build())
                .skipJavaLangImports(true)
                .build();
        return Stream.of(new GeneratorResult.Java(
                new GeneratorResult.Metadata(javaFile.packageName(), javaFile.typeSpec().name()),
                javaFile.toString()));
    }

    private static MethodSpec buildMapType(ValidatedGeneratorElement validElement) {
        var parameterName = "value";

        // The method name and the parameter type ask the same question of either payload; only the
        // null guard differs — a primitive parameter can never arrive null.
        var map = MethodSpec.methodBuilder(String.format("map%sTo%s",
                        validElement.payload().identifier(), validElement.name().flatName()))
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .returns(TypeName.get(validElement.element().asType()))
                .addParameter(TypeName.get(validElement.payloadType()), parameterName);

        if (!validElement.isPayloadPrimitive()) {
            map.beginControlFlow("if(%s == null)".formatted(parameterName))
                    .addStatement("return null")
                    .endControlFlow();
        }

        return map.addStatement("return $L", code(validElement.java().create(parameterName))).build();
    }

    private static MethodSpec buildMapToPayload(ValidatedGeneratorElement validElement) {
        var parameterName = "type";

        var mapToPayload = MethodSpec.methodBuilder(String.format("map%sTo%s",
                        validElement.name().flatName(), validElement.payload().identifier()))
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .returns(TypeName.get(validElement.payloadType()))
                .addParameter(TypeName.get(validElement.element().asType()), parameterName);

        // Same condition as above, different reason: there the payload was the parameter and a
        // primitive one can never arrive null, here it is the return type and a primitive one cannot
        // carry null back out — `return null` from a method returning `int` would not compile.
        if (!validElement.isPayloadPrimitive()) {
            mapToPayload.beginControlFlow("if(%s == null)".formatted(parameterName))
                    .addStatement("return null")
                    .endControlFlow();
        }

        return mapToPayload
                .addStatement("return $L", code(validElement.java().read(parameterName)))
                .build();
    }
}
