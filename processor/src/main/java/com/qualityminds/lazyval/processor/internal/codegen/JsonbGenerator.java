package com.qualityminds.lazyval.processor.internal.codegen;

import com.palantir.javapoet.*;
import com.qualityminds.lazyval.collections.NonEmptySet;
import com.qualityminds.lazyval.processor.spi.Generator;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;
import com.qualityminds.lazyval.processor.spi.StockGeneratorIds;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.qualityminds.lazyval.processor.spi.GeneratorResult.Metadata;

// must only be public for ServiceLoader, but it is not part of the API
public class JsonbGenerator implements Generator {

    private static final String OPTION_GENERATED_PACKAGE = "lazyval.jsonb.package";
    private static final String OPTION_REGISTER = "lazyval.jsonb.register";

    private static final String JSONB_ADAPTER_PACKAGE = "jakarta.json.bind.adapter";
    private static final String JSONB_CONFIG_PACKAGE = "jakarta.json.bind";
    private static final String JAXRS_PROVIDER_PACKAGE = "jakarta.ws.rs.ext";
    private static final String CONTEXT_RESOLVER_FQCN = "jakarta.ws.rs.ext.ContextResolver";
    private static final String QUARKUS_CUSTOMIZER_FQCN = "io.quarkus.jsonb.JsonbConfigCustomizer";
    private static final String QUARKUS_CUSTOMIZER_PACKAGE = "io.quarkus.jsonb";

    private static final AnnotationSpec OVERRIDE_ANNOTATION = AnnotationSpec.builder(
            ClassName.get("java.lang", "Override")).build();


    @Override
    public String generatorId() {
        return StockGeneratorIds.JSONB;
    }

    @Override
    public Set<String> requiredClasspath() {
        return Set.of("jakarta.json.bind.adapter.JsonbAdapter");
    }

    @Override
    public Set<String> supportedOptions() {
        return Set.of(OPTION_GENERATED_PACKAGE, OPTION_REGISTER);
    }

    @Override
    public Stream<GeneratorResult> generate(NonEmptySet<ValidatedGeneratorElement> elements, Context context) {
        List<TypeSpec> adapters = new ArrayList<>(elements.size());
        elements.forEach(element -> adapters.add(generateAdapter(element)));

        boolean isQuarkus = context.isOnClasspath(QUARKUS_CUSTOMIZER_FQCN);
        var typeSpec = generateProvider(adapters, isQuarkus);

        final String packageName = context.generatorPackage(OPTION_GENERATED_PACKAGE, null);

        final JavaFile javaFile = JavaFile.builder(packageName, typeSpec)
                .skipJavaLangImports(true)
                .build();
        var fileMetadata = new Metadata(javaFile.packageName(), javaFile.typeSpec().name());

        var results = Stream.<GeneratorResult>builder();
        results.add(new GeneratorResult.Java(fileMetadata, javaFile.toString()));

        // On Quarkus the JsonbConfigCustomizer above is the idiomatic registration path;
        // a JAX-RS ContextResolver would double-register the same adapters via REST-easy.
        boolean register = context.getSetting(OPTION_REGISTER).map(Boolean::parseBoolean).orElse(true);
        if (register && !isQuarkus && context.isOnClasspath(CONTEXT_RESOLVER_FQCN)) {
            var resolverSpec = generateContextResolver(fileMetadata);
            var resolverFile = JavaFile.builder(packageName, resolverSpec)
                    .skipJavaLangImports(true)
                    .build();
            var resolverMetadata = new Metadata(resolverFile.packageName(), resolverFile.typeSpec().name());
            results.add(new GeneratorResult.Java(resolverMetadata, resolverFile.toString()));
        }

        return results.build();
    }

    private static TypeSpec generateAdapter(ValidatedGeneratorElement element) {
        var elementType = TypeName.get(element.element().asType());
        var wrappedType = element.wrappedType();
        var adapterName = element.typeName().name() + "Adapter";

        // For JsonbAdapter<Original, Adapted>, Adapted must be a reference type
        TypeName adaptedType;
        if (wrappedType.isPrimitive()) {
            adaptedType = TypeName.get(wrappedType.typeMirror()).box();
        } else {
            adaptedType = TypeName.get(wrappedType.typeMirror());
        }

        var adaptToJson = MethodSpec.methodBuilder("adaptToJson")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .returns(adaptedType)
                .addParameter(elementType, "obj")
                .addException(Exception.class)
                .addStatement("return obj.$L", element.accessor())
                .build();

        var adaptFromJson = MethodSpec.methodBuilder("adaptFromJson")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .returns(elementType)
                .addParameter(adaptedType, "value")
                .addException(Exception.class)
                .addStatement("return $L", element.objectCreation("value"))
                .build();

        return TypeSpec.classBuilder(adapterName)
                .addAnnotation(GeneratedStamp.forGenerator(JsonbGenerator.class))
                .addSuperinterface(ParameterizedTypeName.get(
                        ClassName.get(JSONB_ADAPTER_PACKAGE, "JsonbAdapter"),
                        elementType, adaptedType))
                .addMethod(adaptToJson)
                .addMethod(adaptFromJson)
                .build();
    }

    private static TypeSpec generateContextResolver(Metadata adaptersMetadata) {
        var contextResolverType = ClassName.get(JAXRS_PROVIDER_PACKAGE, "ContextResolver");
        var jsonbType = ClassName.get(JSONB_CONFIG_PACKAGE, "Jsonb");
        var jsonbBuilderType = ClassName.get(JSONB_CONFIG_PACKAGE, "JsonbBuilder");
        var adaptersType = ClassName.get(adaptersMetadata.packageName(), adaptersMetadata.className());
        var providerAnnotation = AnnotationSpec.builder(ClassName.get(JAXRS_PROVIDER_PACKAGE, "Provider")).build();

        // Jsonb instances are expensive to create and thread-safe per the JSON-B spec, so the
        // resolver caches a single instance instead of rebuilding it on every JAX-RS lookup.
        var jsonbField = FieldSpec.builder(jsonbType, "jsonb", Modifier.PRIVATE, Modifier.FINAL)
                .initializer("$T.create($T.config())", jsonbBuilderType, adaptersType)
                .build();

        var getContextMethod = MethodSpec.methodBuilder("getContext")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .returns(jsonbType)
                .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(Object.class)), "type")
                .addStatement("return this.jsonb")
                .build();

        return TypeSpec.classBuilder("LazyvalJsonbContextResolver")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(GeneratedStamp.forGenerator(JsonbGenerator.class))
                .addAnnotation(providerAnnotation)
                .addSuperinterface(ParameterizedTypeName.get(contextResolverType, jsonbType))
                .addField(jsonbField)
                .addMethod(getContextMethod)
                .build();
    }

    private static TypeSpec generateProvider(List<TypeSpec> adapters, boolean isQuarkus) {
        var configType = ClassName.get(JSONB_CONFIG_PACKAGE, "JsonbConfig");
        var adapterArrayType = ArrayTypeName.of(ClassName.get(JSONB_ADAPTER_PACKAGE, "JsonbAdapter"));

        var adaptersMethod = MethodSpec.methodBuilder("adapters")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(adapterArrayType);

        var codeBuilder = CodeBlock.builder().add("return new $T{\n", adapterArrayType);
        for (int i = 0; i < adapters.size(); i++) {
            codeBuilder.add("    new $L()", adapters.get(i).name());
            if (i < adapters.size() - 1) {
                codeBuilder.add(",");
            }
            codeBuilder.add("\n");
        }
        codeBuilder.add("}");
        adaptersMethod.addStatement(codeBuilder.build());

        var configMethod = MethodSpec.methodBuilder("config")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(configType)
                .addStatement("return new $T().withAdapters(adapters())", configType)
                .build();

        var providerBuilder = TypeSpec.classBuilder("LazyvalJsonbAdapters")
                .addModifiers(Modifier.PUBLIC)
                .addMethod(adaptersMethod.build())
                .addMethod(configMethod);

        if (isQuarkus) {
            providerBuilder
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("jakarta.inject", "Singleton")).build())
                    .addSuperinterface(ClassName.get(QUARKUS_CUSTOMIZER_PACKAGE, "JsonbConfigCustomizer"))
                    .addMethod(buildQuarkusCustomizer(configType));
        }

        adapters.forEach(a -> providerBuilder.addType(a.toBuilder().addModifiers(Modifier.STATIC).build()));

        return providerBuilder.build();
    }

    private static MethodSpec buildQuarkusCustomizer(ClassName configType) {
        return MethodSpec.methodBuilder("customize")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.VOID)
                .addParameter(configType, "jsonbConfig")
                .addStatement("jsonbConfig.withAdapters(adapters())")
                .build();
    }
}
