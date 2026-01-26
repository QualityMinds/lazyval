package de.qualityminds.lazyval.processor.codegen;

import com.palantir.javapoet.*;
import de.qualityminds.lazyval.collections.NonEmptySet;
import de.qualityminds.lazyval.processor.spi.GeneratorResult;
import de.qualityminds.lazyval.processor.spi.SingleFileGenerator;
import de.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.Collection;
import java.util.List;

final public class MapstructGenerator implements SingleFileGenerator {

    private static final String GENERATOR_ID = "mapstruct";
    private static final String OPTION_GENERATED_PACKAGE = "lazyval.mapstruct.generatedPackage";

    @Override
    public String generatorId() {
        return GENERATOR_ID;
    }

    @Override
    public Collection<String> requiredClasspath() {
        return List.of("org.mapstruct.Mapper");
    }

    public GeneratorResult generateSingleFile(NonEmptySet<ValidatedGeneratorElement> elements, Settings userSettings){
        var mapperAnnotationBuilder = AnnotationSpec.builder(ClassName.get("org.mapstruct", "Mapper"))
                .addMember("unmappedTargetPolicy", "$L", "org.mapstruct.ReportingPolicy.ERROR");

        TypeSpec.Builder typeSpecBuilder = TypeSpec.interfaceBuilder("LazyvalMapper")
                .addAnnotation(mapperAnnotationBuilder.build())
                .addModifiers(Modifier.PUBLIC);

        for(ValidatedGeneratorElement valid : elements){
            var lazyvalElement = valid.element();

            TypeMirror type = lazyvalElement.asType();
            TypeMirror wrappedType = valid.wrappedType();

            final MethodSpec mapToWrappedType;
            if(wrappedType.getKind().isPrimitive()) {
                mapToWrappedType = MethodSpec.methodBuilder(String.format("map%sToWrappedType", lazyvalElement.getSimpleName()))
                        .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                        .returns(TypeName.get(wrappedType))
                        .addParameter(TypeName.get(type), "type")
                        .addStatement(String.format("return type.%s()", valid.wrappedTypeName()))
                        .build();
            } else {
                mapToWrappedType = MethodSpec.methodBuilder(String.format("map%sToWrappedType", lazyvalElement.getSimpleName()))
                        .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                        .returns(TypeName.get(wrappedType))
                        .addParameter(TypeName.get(type), "type")
                        .beginControlFlow("if(type == null)")
                        .addStatement("return null")
                        .endControlFlow()
                        .addStatement(String.format("return type.%s()", valid.wrappedTypeName()))
                        .build();
            }


            var lazyvalTypeName = TypeName.get(type);
            var objectCreation = String.format("new %s(value)", lazyvalTypeName);
            if(valid.factoryMethod().isPresent()){
                var method = valid.factoryMethod().get();
                objectCreation = String.format("%s.%s(value)", lazyvalTypeName, method.getSimpleName());
            }

            final MethodSpec map;
            if(wrappedType.getKind().isPrimitive()){
                map = MethodSpec.methodBuilder(String.format("map%s", lazyvalElement.getSimpleName()))
                        .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                        .returns(TypeName.get(type))
                        .addParameter(TypeName.get(wrappedType), "value")
                        .addStatement("return $L", objectCreation)
                        .build();
            } else {
                map = MethodSpec.methodBuilder(String.format("map%s", lazyvalElement.getSimpleName()))
                        .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                        .returns(TypeName.get(type))
                        .addParameter(TypeName.get(wrappedType), "value")
                        .beginControlFlow("if(value == null)")
                        .addStatement("return null")
                        .endControlFlow()
                        .addStatement("return $L", objectCreation)
                        .build();
            }

            typeSpecBuilder.addMethod(mapToWrappedType).addMethod(map);
        }

        String mapperPackage = userSettings.get(OPTION_GENERATED_PACKAGE)
                .orElse(String.format("%s", extractRootPackage(elements.stream()
                        .findFirst()
                        .map(ValidatedGeneratorElement::element)
                        .orElseThrow())));

        return new GeneratorResult.Java(JavaFile.builder(mapperPackage, typeSpecBuilder.build()).build());
    }

}
