package com.qualityminds.lazyval.processor.internal.codegen.jackson;

import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.qualityminds.lazyval.collections.NonEmptySet;
import com.qualityminds.lazyval.processor.spi.Generator;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.qualityminds.lazyval.processor.spi.GeneratorResult.Metadata;

public class Jackson3Generator implements Generator {

    public static final String GENERATOR_ID = "jackson-3";
    private static final String OPTION_GENERATED_PACKAGE = "lazyval.jackson.package";

    private static final GeneratorConfig VERSION = GeneratorConfig.JACKSON_3;
    private final JacksonCodegen codegen = new JacksonCodegen(VERSION);

    @Override
    public String generatorId() {
        return GENERATOR_ID;
    }

    @Override
    public Set<String> requiredClasspath() {
        return Set.of("tools.jackson.databind.JacksonModule");
    }

    @Override
    public Set<String> supportedOptions() {
        return Set.of(OPTION_GENERATED_PACKAGE);
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public Stream<GeneratorResult> generate(NonEmptySet<ValidatedGeneratorElement> elements, Context context) {
        List<TypeSpec> serializers = new ArrayList<>(elements.size());
        List<TypeSpec> deserializers = new ArrayList<>(elements.size());
        List<TypeName> elementTypes = new ArrayList<>(elements.size());
        elements.forEach(element -> {
            serializers.add(codegen.generateSerializer(element));
            deserializers.add(codegen.generateDeserializer(element));
            elementTypes.add(TypeName.get(element.element().asType()));
        });

        var typeSpec = codegen.generateModule(serializers, deserializers, elementTypes, context);

        final String packageName = context.generatorPackage(OPTION_GENERATED_PACKAGE, null);

        final JavaFile javaFile = JavaFile.builder(packageName, typeSpec)
                .skipJavaLangImports(true)
                .build();
        var fileMetadata = new Metadata(javaFile.packageName(), javaFile.typeSpec().name());
        return Stream.of(
                new GeneratorResult.ServiceLoader(
                        new Metadata(VERSION.spiPackage(), VERSION.spiClass()),
                        fileMetadata),
                new GeneratorResult.Java(fileMetadata, javaFile.toString())
        );
    }
}
