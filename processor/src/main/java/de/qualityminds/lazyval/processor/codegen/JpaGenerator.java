package de.qualityminds.lazyval.processor.codegen;

import com.palantir.javapoet.*;
import de.qualityminds.lazyval.processor.spi.SpiGenerator;
import de.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;
import de.qualityminds.lazyval.processor.spi.MultipleFilesGenerator;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.Collection;
import java.util.List;

// tag::docu[]
public class JpaGenerator implements MultipleFilesGenerator {

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

    public JavaFile generateFilePerType(ValidatedGeneratorElement valid, Settings userSettings){
        // end::docu[]
        TypeElement element = valid.element();

        TypeMirror type = element.asType();
        TypeMirror wrappedType = valid.wrappedType();
        TypeName wrappedTypeName;
        if (wrappedType.getKind().isPrimitive()) {
            // Box primitive types for JPA generics
            wrappedTypeName = TypeName.get(wrappedType).box();
        } else {
            wrappedTypeName = TypeName.get(wrappedType);
        }

        final MethodSpec convertToDatabaseColumn;
        if(wrappedType.getKind().isPrimitive()) {
            convertToDatabaseColumn = MethodSpec.methodBuilder("convertToDatabaseColumn")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(wrappedTypeName)
                    .addParameter(TypeName.get(type), "type")
                    .addStatement(String.format("return type.%s()", valid.wrappedTypeName()))
                    .build();
        }else {
            convertToDatabaseColumn = MethodSpec.methodBuilder("convertToDatabaseColumn")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(wrappedTypeName)
                    .addParameter(TypeName.get(type), "type")
                    .beginControlFlow("if(type == null)")
                    .addStatement("return null")
                    .endControlFlow()
                    .addStatement(String.format("return type.%s()", valid.wrappedTypeName()))
                    .build();
        }

        var lazyvalTypeName = TypeName.get(type);
        var objectCreation = String.format("new %s(dbValue)", lazyvalTypeName);
        if(valid.factoryMethod().isPresent()){
            var method = valid.factoryMethod().get();
            objectCreation = String.format("%s.%s(dbValue)", lazyvalTypeName, method.getSimpleName());
        }

        final MethodSpec convertToEntityAttribute;
        if(wrappedType.getKind().isPrimitive()){
            convertToEntityAttribute = MethodSpec.methodBuilder("convertToEntityAttribute")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(TypeName.get(type))
                    .addParameter(wrappedTypeName, "dbValue")
                    .addStatement("return $L", objectCreation)
                    .build();
        } else {
            convertToEntityAttribute = MethodSpec.methodBuilder("convertToEntityAttribute")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(TypeName.get(type))
                    .addParameter(wrappedTypeName, "dbValue")
                    .beginControlFlow("if(dbValue == null)")
                    .addStatement("return null")
                    .endControlFlow()
                    .addStatement("return $L", objectCreation)
                    .build();
        }

        TypeSpec jpaConverter = TypeSpec.classBuilder(element.getSimpleName() + "AttributeConverter")
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
                .orElse(String.format("%s.boundary.persistence", extractRootPackage(element)));
        if(jpaConverterPackage.charAt(0) == '.'){
            jpaConverterPackage = jpaConverterPackage.substring(1);
        }
        return JavaFile.builder(jpaConverterPackage, jpaConverter).build();
    }
}
