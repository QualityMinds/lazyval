package com.acme.lazyval.generator;

import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import de.qualityminds.lazyval.processor.spi.FilePerTypeGenerator;
import de.qualityminds.lazyval.processor.spi.GeneratorResult;
import de.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;
import org.jspecify.annotations.NullMarked;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.Collection;
import java.util.List;

@NullMarked
public class UtilsGenerator implements FilePerTypeGenerator {

    private static final String OPTION_GENERATED_PACKAGE = "acme.some.lazyval.generatedPackage";

    @Override
    public String generatorId() {
        return "acme-utils";
    }

    @Override
    public GeneratorResult generateFilePerType(ValidatedGeneratorElement validatedElement, Settings userSettings) {

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

        if(!wrappedTypeName.equals(TypeName.get(String.class))){
            return new GeneratorResult.Nothing();
        }

        final MethodSpec toUppercase = MethodSpec.methodBuilder("toUpperCase")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(wrappedTypeName)
                .addParameter(TypeName.get(type), "type")
                .beginControlFlow("if(type == null)")
                .addStatement("return null")
                .endControlFlow()
                .addStatement(String.format("return type.%s().toUpperCase()", validatedElement.wrappedTypeName()))
                .build();


        TypeSpec someClass = TypeSpec.classBuilder(element.getSimpleName() + "Utils")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(toUppercase)
                .build();

        String userProvidedPackage = userSettings.get(OPTION_GENERATED_PACKAGE)
                .orElse(String.format("%s.test", extractRootPackage(element)));
        if(userProvidedPackage.charAt(0) == '.'){
            userProvidedPackage = userProvidedPackage.substring(1);
        }
        var javaFile = JavaFile.builder(userProvidedPackage, someClass).build();
        return new GeneratorResult.Java(
                new GeneratorResult.Metadata(userProvidedPackage, someClass.name()),
                javaFile.toString());
    }

    @Override
    public Collection<String> requiredClasspath() {
        return List.of();
    }
}
