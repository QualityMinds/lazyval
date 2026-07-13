package com.qualityminds.lazyval.processor.internal.codegen.beanvalidation;

import com.palantir.javapoet.*;
import com.qualityminds.lazyval.collections.NonEmptySet;
import com.qualityminds.lazyval.processor.internal.codegen.GeneratedStamp;
import com.qualityminds.lazyval.processor.spi.Generator;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Generates a {@code ValueExtractor} for each domain-primitive, delegating all constraint
 * validation back to the Bean Validation provider's built-in validators.
 *
 * <p>A single extractor unwraps the inner value so that every constraint annotation the provider
 * supports for the inner type (e.g. {@code @Email}, {@code @Pattern}, {@code @Min}, {@code @Past})
 * works transparently on the domain-primitive — including full provider-level validation logic,
 * correct flag handling, and RFC-compliant email checking.
 *
 * <p>The extractor is registered via
 * {@code META-INF/services/jakarta.validation.valueextraction.ValueExtractor} and is therefore
 * discovered automatically by any compliant Bean Validation provider (like Hibernate Validator).
 */
public class BeanValidationGenerator implements Generator {

    private static final String GENERATOR_ID = "beanvalidation";
    private static final String OPTION_GENERATED_PACKAGE = "lazyval.beanvalidation.package";

    private static final ClassName VALUE_EXTRACTOR =
            ClassName.get("jakarta.validation.valueextraction", "ValueExtractor");
    private static final ClassName EXTRACTED_VALUE =
            ClassName.get("jakarta.validation.valueextraction", "ExtractedValue");
    private static final ClassName VALUE_RECEIVER =
            ClassName.get("jakarta.validation.valueextraction", "ValueExtractor", "ValueReceiver");
    private static final AnnotationSpec UNWRAP_ANNOTATION = AnnotationSpec.builder(
            ClassName.get("jakarta.validation.valueextraction", "UnwrapByDefault"))
            .build();


    @Override
    public String generatorId() {
        return GENERATOR_ID;
    }

    @Override
    public Set<String> requiredClasspath() {
        return Set.of("jakarta.validation.valueextraction.ValueExtractor");
    }

    @Override
    public Set<String> supportedOptions() {
        return Set.of(OPTION_GENERATED_PACKAGE);
    }

    @Override
    public Stream<GeneratorResult> generate(NonEmptySet<ValidatedGeneratorElement> elements, Context context) {
        final String packageName = context.generatorPackage(OPTION_GENERATED_PACKAGE, null);
        return elements.stream().flatMap(element -> buildValueExtractor(element, packageName));
    }

    private Stream<GeneratorResult> buildValueExtractor(ValidatedGeneratorElement element, String packageName) {
        TypeMirror lazyvalTypeMirror = element.element().asType();
        TypeMirror wrappedTypeMirror = element.wrappedType().typeMirror();
        String className = element.typeName().name() + "ValueExtractor";

        // For primitive inner types (int, long, ...) the class literal in @ExtractedValue must be boxed.
        TypeName wrappedTypeName = TypeName.get(wrappedTypeMirror);
        TypeName wrappedTypeForAnnotation = element.wrappedType().isPrimitive()
                ? wrappedTypeName.box()
                : wrappedTypeName;

        AnnotationSpec extractedValueAnnotation = AnnotationSpec.builder(EXTRACTED_VALUE)
                .addMember("type", "$T.class", wrappedTypeForAnnotation)
                .build();

        // ValueExtractor<@ExtractedValue(type = String.class) Isbn>
        TypeName annotatedLazyvalType = TypeName.get(lazyvalTypeMirror)
                .annotated(List.of(extractedValueAnnotation));
        TypeName superInterface = ParameterizedTypeName.get(VALUE_EXTRACTOR, annotatedLazyvalType);

        MethodSpec extractValues = MethodSpec.methodBuilder("extractValues")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(TypeName.get(lazyvalTypeMirror), "originalValue")
                .addParameter(VALUE_RECEIVER, "receiver")
                .beginControlFlow("if (originalValue == null)")
                .addStatement("receiver.value(null, null)")
                .addStatement("return")
                .endControlFlow()
                .addStatement("receiver.value(null, originalValue.$L)", element.accessor())
                .build();

        TypeSpec extractor = TypeSpec.classBuilder(className)
                .addAnnotation(GeneratedStamp.forGenerator(BeanValidationGenerator.class))
                .addAnnotation(UNWRAP_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(superInterface)
                .addMethod(extractValues)
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, extractor)
                .skipJavaLangImports(true)
                .build();
        return toResultStream(javaFile, packageName, className);
    }


    static Stream<GeneratorResult> toResultStream(JavaFile javaFile, String packageName, String className) {
        var metadata = new GeneratorResult.Metadata(packageName, className);
        var javaResult = new GeneratorResult.Java(metadata, javaFile.toString());
        var serviceLoaderResult = new GeneratorResult.ServiceLoader(
                new GeneratorResult.Metadata("jakarta.validation.valueextraction", "ValueExtractor"),
                metadata
        );
        return Stream.of(javaResult, serviceLoaderResult);
    }
}
