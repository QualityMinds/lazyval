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
        if (!context.isOnClasspath("org.springframework.data.cassandra.core.convert.CassandraCustomConversions")) {
            return Stream.empty();
        }

        final String converterPackage = context.generatorPackage(OPTION_GENERATED_PACKAGE, "boundary.persistence.cassandra");

        List<TypeSpec> converterSpecs = new ArrayList<>();
        for (ValidatedGeneratorElement element : elements) {
            converterSpecs.add(buildReadConverter(element));
            converterSpecs.add(buildWriteConverter(element));
        }

        TypeSpec configSpec = buildSpringDataConfiguration(converterSpecs, context);
        JavaFile configFile = JavaFile.builder(converterPackage, configSpec).build();

        return Stream.of(new GeneratorResult.Java(
                new GeneratorResult.Metadata(configFile.packageName(), configFile.typeSpec().name()),
                configFile.toString()));
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
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
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
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .addAnnotation(WRITING_CONVERTER)
                .addSuperinterface(ParameterizedTypeName.get(CONVERTER, elementTypeName, wrappedTypeName))
                .addMethod(convertMethod)
                .build();
    }

    private static TypeSpec buildSpringDataConfiguration(List<TypeSpec> converterSpecs, Context context) {
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
        for (int i = 0; i < converterSpecs.size(); i++) {
            listInit.append("    new ").append(converterSpecs.get(i).name()).append("()");
            if (i < converterSpecs.size() - 1) {
                listInit.append(",");
            }
            listInit.append("\n");
        }
        listInit.append(")");
        beanMethod.addStatement(listInit.toString(), listClass, converterWildcard, listClass);
        beanMethod.addStatement("return new $T(converters)", CASSANDRA_CUSTOM_CONVERSIONS);

        TypeSpec.Builder configBuilder = TypeSpec.classBuilder("LazyvalSpringDataConfiguration")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(CONFIGURATION)
                .addJavadoc("""
                        Generated Spring Data converter configuration.
                        <p>
                        Registers all read/write converters for types annotated with {@code @Lazyval}
                        with the appropriate Spring Data store-specific conversion service.
                        <p>
                        Generated by the Lazyval annotation processor. Do not modify.
                        """);

        configBuilder.addMethod(beanMethod.build());
        for (TypeSpec converterSpec : converterSpecs) {
            configBuilder.addType(converterSpec);
        }

        return configBuilder.build();
    }
}
