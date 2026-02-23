package com.acme.lazyval.generator;

import com.qualityminds.lazyval.processor.spi.FilePerTypeGenerator;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;
import org.jspecify.annotations.NullMarked;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@NullMarked
public class UtilsGenerator implements FilePerTypeGenerator {

    private static final String OPTION_GENERATED_PACKAGE = "acme.some.lazyval.generatedPackage";

    @Override
    public String generatorId() {
        return "acme-utils";
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
    public GeneratorResult generateFilePerType(ValidatedGeneratorElement validatedElement, Settings userSettings) {

        TypeElement element = validatedElement.element();
        TypeMirror wrappedType = validatedElement.wrappedType();
        // this generator should only handle String types
        if (!"java.lang.String".equals(wrappedType.toString())) {
            return new GeneratorResult.Nothing();
        }

        String className = element.getSimpleName() + "Utils";
        String packageName = userSettings.get(OPTION_GENERATED_PACKAGE)
                .orElse(String.format("%s.test", extractRootPackage(element)));
        if(packageName.charAt(0) == '.'){
            packageName = packageName.substring(1);
        }

        var contents = ("package %s;\n\n".formatted(packageName) +
                "import %s;\n\n".formatted(element.getQualifiedName()) +
                "public final class %s {\n".formatted(className) +
                """
                            public static %s toUpperCase(%s type) {
                            if(type == null) {
                              return null;
                            }
                            return type.%s().toUpperCase();
                          }
                        """.formatted(
                        wrappedType.toString(), element.getSimpleName(), validatedElement.wrappedTypeName()) +
                "}").replaceAll("\\n", System.lineSeparator());


        return new GeneratorResult.Java(
                new GeneratorResult.Metadata(packageName, className),
                contents);
    }
}
