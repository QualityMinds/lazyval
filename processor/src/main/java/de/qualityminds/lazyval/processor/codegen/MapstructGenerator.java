package de.qualityminds.lazyval.processor.codegen;

import com.palantir.javapoet.*;
import de.qualityminds.lazyval.processor.LazyvalEnvironment;
import de.qualityminds.lazyval.processor.ValidatedGeneratorElement;
import de.qualityminds.lazyval.processor.spi.SingleFileGenerator;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

final public class MapstructGenerator implements SingleFileGenerator {

    public MapstructGenerator(){
        // needed for ServiceLoader
    }

    public Optional<JavaFile> generateSingleFile(Set<ValidatedGeneratorElement> elements, LazyvalEnvironment layzvalEnvironment){
        if(layzvalEnvironment.isMapstructMissingOnClasspath()){
            layzvalEnvironment.info("Mapstruct is not on classpath. Lazyval will not generate Mapstruct mappers.");
            return Optional.empty();
        }

        if(elements.isEmpty()){
            return Optional.empty();
        }

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

        String mapperPackage = layzvalEnvironment.getSettings().getMapstructPackage()
                .orElse(String.format("%s", LazyvalEnvironment.extractRootPackage(elements.stream()
                        .findFirst()
                        .map(ValidatedGeneratorElement::element)
                        .orElseThrow())));

        return Optional.of(JavaFile.builder(mapperPackage, typeSpecBuilder.build()).build());
    }

}
