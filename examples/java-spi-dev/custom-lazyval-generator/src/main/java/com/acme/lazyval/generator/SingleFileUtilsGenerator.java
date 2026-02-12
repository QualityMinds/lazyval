package com.acme.lazyval.generator;

import de.qualityminds.lazyval.collections.NonEmptySet;
import de.qualityminds.lazyval.processor.spi.GeneratorResult;
import de.qualityminds.lazyval.processor.spi.SingleFileGenerator;
import de.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;
import org.jspecify.annotations.NullMarked;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@NullMarked
public class SingleFileUtilsGenerator implements SingleFileGenerator {

    private static final String OPTION_GENERATED_PACKAGE = "acme.some.lazyval.generatedPackage";

    @Override
    public String generatorId() {
        return "acme-utils-single";
    }

    @Override
    public Collection<String> requiredClasspath() {
        return List.of();
    }

    @Override
    public Set<String> supportedOptions() {
        return Set.of(OPTION_GENERATED_PACKAGE);
    }

    @Override
    public GeneratorResult generateSingleFile(NonEmptySet<ValidatedGeneratorElement> elements, Settings userSettings) {

        List<String> imports = new ArrayList<>(elements.size());
        List<String> methods = new ArrayList<>(elements.size());

        elements.stream()
                .filter(ve -> "java.lang.String".equals(ve.wrappedType().toString()))
                .forEach(validatedElement -> {
                    TypeElement element = validatedElement.element();
                    TypeMirror wrappedType = validatedElement.wrappedType();
                    imports.add("import %s;\n".formatted(element.getQualifiedName()));

                    var method = """
                            public static %s toUpperCase(%s type) {
                            if(type == null) {
                              return null;
                            }
                            return type.%s().toUpperCase();
                          }
                        """.formatted(
                            wrappedType.toString(), element.getSimpleName(), validatedElement.wrappedTypeName());
                    methods.add(method);
                });

        String packageName = userSettings.get(OPTION_GENERATED_PACKAGE)
                // any element suffices to create the package
                .orElse(String.format("%s.test", extractRootPackage(elements.getAny().element())));
        if(packageName.charAt(0) == '.'){
            packageName = packageName.substring(1);
        }

        var contents = ("package %s;\n\n".formatted(packageName) +
                String.join("\n", imports) +
                "public final class Utils {\n" +
                String.join("\n", methods) +
                "}").replaceAll("\\n", System.lineSeparator());


        return new GeneratorResult.Java(
                new GeneratorResult.Metadata(packageName, "Utils"),
                contents);
    }
}
