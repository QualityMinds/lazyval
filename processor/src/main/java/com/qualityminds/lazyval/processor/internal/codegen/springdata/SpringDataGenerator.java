package com.qualityminds.lazyval.processor.internal.codegen.springdata;

import com.palantir.javapoet.*;
import com.qualityminds.lazyval.collections.NonEmptySet;
import com.qualityminds.lazyval.processor.spi.Generator;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

// must only be public for ServiceLoader, but it is not part of the API
public class SpringDataGenerator implements Generator {

    private static final String GENERATOR_ID = "spring-data";
    private static final String OPTION_GENERATED_PACKAGE = "lazyval.spring_data.package";

    private static final ClassName READING_CONVERTER = ClassName.get("org.springframework.data.convert", "ReadingConverter");
    private static final ClassName WRITING_CONVERTER = ClassName.get("org.springframework.data.convert", "WritingConverter");
    private static final ClassName CONVERTER = ClassName.get("org.springframework.core.convert.converter", "Converter");
    private static final ClassName CONFIGURATION = ClassName.get("org.springframework.context.annotation", "Configuration");
    private static final ClassName BEAN = ClassName.get("org.springframework.context.annotation", "Bean");
    private static final ClassName CASSANDRA_CUSTOM_CONVERSIONS = ClassName.get(
            "org.springframework.data.cassandra.core.convert", "CassandraCustomConversions");

    private static final AnnotationSpec OVERRIDE_ANNOTATION = AnnotationSpec.builder(
            ClassName.get("java.lang", "Override")).build();

    @Override
    public String generatorId() {
        return GENERATOR_ID;
    }

    @Override
    public Collection<String> requiredClasspath() {
        return List.of("org.springframework.data.convert.ReadingConverter");
    }

    @Override
    public Set<String> supportedOptions() {
        return Set.of(OPTION_GENERATED_PACKAGE);
    }

    @Override
    public Stream<GeneratorResult> generate(NonEmptySet<ValidatedGeneratorElement> elements, Context context) {
        final String converterPackage = context.generatorPackage(OPTION_GENERATED_PACKAGE, "boundary.persistence.cassandra");

        List<GeneratorResult> results = new ArrayList<>();
        List<String> converterClassNames = new ArrayList<>();

        for (ValidatedGeneratorElement element : elements) {
            TypeSpec readConverter = buildReadConverter(element);
            TypeSpec writeConverter = buildWriteConverter(element);

            JavaFile readFile = JavaFile.builder(converterPackage, readConverter).build();
            JavaFile writeFile = JavaFile.builder(converterPackage, writeConverter).build();

            results.add(new GeneratorResult.Java(
                    new GeneratorResult.Metadata(readFile.packageName(), readFile.typeSpec().name()),
                    readFile.toString()));
            results.add(new GeneratorResult.Java(
                    new GeneratorResult.Metadata(writeFile.packageName(), writeFile.typeSpec().name()),
                    writeFile.toString()));

            converterClassNames.add(readConverter.name());
            converterClassNames.add(writeConverter.name());
        }

        if (!converterClassNames.isEmpty()) {
            if (context.isOnClasspath("org.springframework.data.cassandra.core.convert.CassandraCustomConversions")) {
                TypeSpec configSpec = buildCassandraConfiguration(converterClassNames, context);
                JavaFile configFile = JavaFile.builder(converterPackage, configSpec).build();
                results.add(new GeneratorResult.Java(
                        new GeneratorResult.Metadata(configFile.packageName(), configFile.typeSpec().name()),
                        configFile.toString()));
            }
        }

        return results.stream();
    }

    private static TypeSpec buildReadConverter(ValidatedGeneratorElement element) {
        TypeMirror type = element.element().asType();
        var wrappedType = element.wrappedType();

        TypeName wrappedTypeName;
        if (wrappedType.isPrimitive()) {
            wrappedTypeName = TypeName.get(wrappedType.typeMirror()).box();
        } else {
            wrappedTypeName = TypeName.get(wrappedType.typeMirror());
        }
        TypeName elementTypeName = TypeName.get(type);

        MethodSpec convertMethod = MethodSpec.methodBuilder("convert")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .returns(elementTypeName)
                .addParameter(wrappedTypeName, "source")
                .addStatement("return $L", element.objectCreation("source"))
                .build();

        return TypeSpec.classBuilder(element.typeName() + "ReadConverter")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(READING_CONVERTER)
                .addSuperinterface(ParameterizedTypeName.get(CONVERTER, wrappedTypeName, elementTypeName))
                .addMethod(convertMethod)
                .build();
    }

    private static TypeSpec buildWriteConverter(ValidatedGeneratorElement element) {
        TypeMirror type = element.element().asType();
        var wrappedType = element.wrappedType();

        TypeName wrappedTypeName;
        if (wrappedType.isPrimitive()) {
            wrappedTypeName = TypeName.get(wrappedType.typeMirror()).box();
        } else {
            wrappedTypeName = TypeName.get(wrappedType.typeMirror());
        }
        TypeName elementTypeName = TypeName.get(type);

        MethodSpec convertMethod = MethodSpec.methodBuilder("convert")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .returns(wrappedTypeName)
                .addParameter(elementTypeName, "source")
                .addStatement("return source.$L", element.accessor())
                .build();

        return TypeSpec.classBuilder(element.typeName() + "WriteConverter")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(WRITING_CONVERTER)
                .addSuperinterface(ParameterizedTypeName.get(CONVERTER, elementTypeName, wrappedTypeName))
                .addMethod(convertMethod)
                .build();
    }

    private static TypeSpec buildCassandraConfiguration(List<String> converterClassNames, Context context) {
        boolean hasConditionalOnMissingBean = context.isOnClasspath(
                "org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean");

        ClassName listClass = ClassName.get("java.util", "List");
        TypeName converterWildcard = ParameterizedTypeName.get(CONVERTER,
                WildcardTypeName.subtypeOf(Object.class), WildcardTypeName.subtypeOf(Object.class));

        MethodSpec.Builder beanMethod = MethodSpec.methodBuilder("cassandraCustomConversions")
                .addAnnotation(BEAN)
                .addModifiers(Modifier.PUBLIC)
                .returns(CASSANDRA_CUSTOM_CONVERSIONS);

        if (hasConditionalOnMissingBean) {
            beanMethod.addAnnotation(ClassName.get(
                    "org.springframework.boot.autoconfigure.condition", "ConditionalOnMissingBean"));
        }

        StringBuilder listInit = new StringBuilder("$T<$T> converters = $T.of(\n");
        for (int i = 0; i < converterClassNames.size(); i++) {
            listInit.append("    new ").append(converterClassNames.get(i)).append("()");
            if (i < converterClassNames.size() - 1) {
                listInit.append(",");
            }
            listInit.append("\n");
        }
        listInit.append(")");
        beanMethod.addStatement(listInit.toString(), listClass, converterWildcard, listClass);
        beanMethod.addStatement("return new $T(converters)", CASSANDRA_CUSTOM_CONVERSIONS);

        TypeSpec.Builder configBuilder = TypeSpec.classBuilder("LazyvalCassandraSpringDataConfiguration")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(CONFIGURATION);

        configBuilder.addMethod(beanMethod.build());

        return configBuilder.build();
    }
}
