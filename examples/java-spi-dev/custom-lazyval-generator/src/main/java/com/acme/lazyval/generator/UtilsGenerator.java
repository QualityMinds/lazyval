package com.acme.lazyval.generator;

import com.qualityminds.lazyval.collections.NonEmptySet;
import com.qualityminds.lazyval.processor.spi.Generator;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@NullMarked
public class UtilsGenerator implements Generator {

    private static final String OPTION_GENERATED_PACKAGE = "acme.some.lazyval.generatedPackage";

    @Override
    public String generatorId() {
        return "acme-utils-single";
    }

    @Override
    public Set<String> requiredClasspath() {
        return Set.of();
    }

    @Override
    public Set<String> supportedOptions() {
        return Set.of(OPTION_GENERATED_PACKAGE);
    }

    @Override
    public Stream<GeneratorResult> generate(NonEmptySet<ValidatedGeneratorElement> elements, Context ctx) {

        List<String> imports = new ArrayList<>(elements.size());
        List<String> methods = new ArrayList<>(elements.size());

        elements.stream()
                .filter(ve -> "String".equals(ve.wrappedType().typeName().toString()))
                .forEach(validatedElement -> {
                    var  wrappedType = validatedElement.wrappedType();
                    imports.add("import %s;\n".formatted(validatedElement.element().getQualifiedName()));

                    var method = """
                            public static %s toUpperCase(%s type) {
                            if(type == null) {
                              return null;
                            }
                            return type.%s.toUpperCase();
                          }
                        """.formatted(
                            wrappedType.typeName(), validatedElement.typeName(), validatedElement.accessor());
                    methods.add(method);
                });

        String packageName = ctx.generatorPackage(OPTION_GENERATED_PACKAGE, null);

        var contents = ("package %s;\n\n".formatted(packageName) +
                String.join("\n", imports) +
                "public final class Utils {\n" +
                String.join("\n", methods) +
                "}").replaceAll("\\n", System.lineSeparator());


        return Stream.of(new GeneratorResult.Java(
                new GeneratorResult.Metadata(packageName, "Utils"),
                contents));
    }
}
