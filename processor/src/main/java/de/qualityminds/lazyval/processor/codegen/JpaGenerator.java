package de.qualityminds.lazyval.processor.codegen;

import com.palantir.javapoet.*;
import de.qualityminds.lazyval.processor.spi.FilePerTypeGenerator;
import de.qualityminds.lazyval.processor.spi.GeneratorResult;
import de.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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

    public GeneratorResult generateFilePerType(ValidatedGeneratorElement validatedElement, Settings userSettings){
        // end::docu[]
        TypeElement element = validatedElement.element();

        TypeMirror type = element.asType();
        TypeMirror wrappedType = validatedElement.wrappedType();
        TypeName wrappedTypeName;
        if (wrappedType.getKind().isPrimitive()) {
            // Box primitive types for JPA generics
            wrappedTypeName = TypeName.get(wrappedType).box();
        } else {
            wrappedTypeName = TypeName.get(wrappedType);
        }

        // Jpa Converter needs to check for null due to boxing primitive types
        final MethodSpec convertToDatabaseColumn = MethodSpec.methodBuilder("convertToDatabaseColumn")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(wrappedTypeName)
                    .addParameter(TypeName.get(type), "type")
                    .beginControlFlow("if(type == null)")
                    .addStatement("return null")
                    .endControlFlow()
                    .addStatement(String.format("return type.%s()", validatedElement.wrappedTypeName()))
                    .build();

        var lazyvalTypeName = TypeName.get(type);
        var objectCreation = String.format("new %s(dbValue)", lazyvalTypeName);
        if(validatedElement.factoryMethod().isPresent()){
            var method = validatedElement.factoryMethod().get();
            objectCreation = String.format("%s.%s(dbValue)", lazyvalTypeName, method.getSimpleName());
        }

        final MethodSpec convertToEntityAttribute = MethodSpec.methodBuilder("convertToEntityAttribute")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(TypeName.get(type))
                    .addParameter(wrappedTypeName, "dbValue")
                    .beginControlFlow("if(dbValue == null)")
                    .addStatement("return null")
                    .endControlFlow()
                    .addStatement("return $L", objectCreation)
                    .build();

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
        final JavaFile javaFile = JavaFile.builder(jpaConverterPackage, jpaConverter).build();
        return new GeneratorResult.Java(
                new GeneratorResult.Metadata(javaFile.packageName(), javaFile.typeSpec().name()),
                javaFile.toString());
    }
}
