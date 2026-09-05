package com.qualityminds.lazyval.processor.internal.codegen.springdata;

import com.palantir.javapoet.*;
import com.qualityminds.lazyval.collections.NonEmptySet;
import com.qualityminds.lazyval.processor.internal.codegen.GeneratedStamp;
import com.qualityminds.lazyval.processor.spi.Generator;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;
import com.qualityminds.lazyval.processor.spi.StockGeneratorIds;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static com.qualityminds.lazyval.processor.internal.codegen.JavaPoetExprs.code;

/**
 * Generates Spring Data {@code Converter} read/write pairs for each domain-primitive and
 * bundles them into a {@code LazyvalSpringDataConfiguration} class that registers them with
 * the store's {@code CustomConversions} bean.
 *
 * <h3>Null invariants</h3>
 * Spring Data's {@code Converter} contract guarantees a non-null {@code source} argument for
 * both read and write converters; null column values are resolved by Spring Data before the
 * converter is invoked and never reach {@code convert}.
 * <p>
 * <b>Read converters:</b> if the factory method returns {@code null} for a non-null DB value
 * (e.g. a blank-string guard), Spring Data propagates {@code null} to the target property
 * silently. Java's type system provides no compile-time guarantee for this; callers must ensure
 * the target field accepts {@code null}.
 * <p>
 * <b>Write converters:</b> the payload inside a domain-primitive is always non-nullable
 * (lazyval rejects nullable payloads at compile time), so write converters always
 * return a non-null value.
 */
// must only be public for ServiceLoader, but it is not part of the API
public class SpringDataGenerator implements Generator {

    private static final String OPTION_GENERATED_PACKAGE = "lazyval.springdata.package";

    private static final String CONVERTER_FQN = "org.springframework.core.convert.converter.Converter";
    private static final String READING_CONVERTER_FQN = "org.springframework.data.convert.ReadingConverter";
    private static final String WRITING_CONVERTER_FQN = "org.springframework.data.convert.WritingConverter";

    private static final ClassName READING_CONVERTER = ClassName.get("org.springframework.data.convert", "ReadingConverter");
    private static final ClassName WRITING_CONVERTER = ClassName.get("org.springframework.data.convert", "WritingConverter");
    private static final ClassName CONVERTER = ClassName.get("org.springframework.core.convert.converter", "Converter");
    private static final ClassName CONFIGURATION = ClassName.get("org.springframework.context.annotation", "Configuration");
    private static final ClassName BEAN = ClassName.get("org.springframework.context.annotation", "Bean");

    private static final AnnotationSpec OVERRIDE_ANNOTATION = AnnotationSpec.builder(
            ClassName.get("java.lang", "Override")).build();

    @Override
    public String generatorId() {
        return StockGeneratorIds.SPRING_DATA;
    }

    @Override
    public Set<String> requiredClasspath() {
        return Set.of(
                "org.springframework.data.convert.ReadingConverter",
                "org.springframework.data.convert.WritingConverter");
    }

    @Override
    public Set<String> supportedOptions() {
        Set<String> options = new LinkedHashSet<>();
        options.add(OPTION_GENERATED_PACKAGE);
        for (SpringDataStore store : SpringDataStore.values()) {
            options.add(store.optionKey());
        }
        return options;
    }

    @Override
    public Set<String> supersedes() {
        return Set.of(StockGeneratorIds.CASSANDRA_CODEC, StockGeneratorIds.MONGODB_CODEC);
    }

    @Override
    public Stream<GeneratorResult> generate(NonEmptySet<ValidatedGeneratorElement> elements, Context context) {
        EnumSet<SpringDataStore> activeStores = Arrays.stream(SpringDataStore.values())
                .filter(store -> context.isOnClasspath(store.conversionsFqn()))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(SpringDataStore.class)));
        if (activeStores.isEmpty()) {
            return Stream.empty();
        }

        final String converterPackage = context.generatorPackage(OPTION_GENERATED_PACKAGE, "boundary.persistence");

        EnumSet.complementOf(activeStores).forEach(store -> warnIfOptionSetForMissingStore(store, context));

        // EnumMap and EnumSet both iterate in declaration order, which is emission order
        Map<SpringDataStore, List<String>> userConverters = new EnumMap<>(SpringDataStore.class);
        activeStores.forEach(store -> userConverters.put(store, validateUserConverters(store, context, converterPackage)));

        boolean hasConditionalOnMissingBean = context.isOnClasspath(
                "org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean");

        List<TypeSpec> converterSpecs = new ArrayList<>();
        for (ValidatedGeneratorElement element : elements) {
            converterSpecs.add(buildReadConverter(element));
            converterSpecs.add(buildWriteConverter(element));
        }

        TypeSpec configSpec = buildSpringDataConfiguration(
                converterSpecs, userConverters, hasConditionalOnMissingBean);
        JavaFile configFile = JavaFile.builder(converterPackage, configSpec)
                .skipJavaLangImports(true)
                .build();

        return Stream.of(new GeneratorResult.Java(
                new GeneratorResult.Metadata(configFile.packageName(), configFile.typeSpec().name()),
                configFile.toString()));
    }

    private List<String> validateUserConverters(SpringDataStore store, Context context, String converterPackage) {
        String optionKey = store.optionKey();
        String raw = context.getSetting(optionKey).orElse("");
        if (raw.isBlank()) {
            return List.of();
        }
        List<String> fqns = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        List<String> valid = new ArrayList<>(fqns.size());
        for (String fqn : fqns) {
            Optional<Context.ClassInspection> inspection = context.inspectClass(fqn);
            if (inspection.isEmpty()) {
                context.logError(this, optionKey + ": class '" + fqn + "' not found on compile classpath");
                continue;
            }
            Context.ClassInspection info = inspection.get();
            boolean ok = true;
            if (!info.isAssignableTo(CONVERTER_FQN)) {
                context.logError(this, optionKey + ": class '" + fqn + "' does not implement " + CONVERTER_FQN);
                ok = false;
            }
            if (!info.isAccessibleFrom(converterPackage)) {
                context.logError(this, optionKey + ": class '" + fqn + "' is not accessible from the generated configuration at package '" + converterPackage + "'");
                ok = false;
            }
            if (!info.hasAccessibleNoArgConstructor(converterPackage)) {
                context.logError(this, optionKey + ": class '" + fqn + "' must declare a no-arg constructor accessible from the generated configuration at package '" + converterPackage + "'");
                ok = false;
            }
            if (!info.hasAnnotation(READING_CONVERTER_FQN) && !info.hasAnnotation(WRITING_CONVERTER_FQN)) {
                context.logError(this, optionKey + ": class '" + fqn + "' must be annotated with @ReadingConverter or @WritingConverter");
                ok = false;
            }
            if (ok) {
                valid.add(fqn);
            }
        }
        return valid;
    }

    private void warnIfOptionSetForMissingStore(SpringDataStore store, Context context) {
        context.getSetting(store.optionKey())
                .filter(v -> !v.isBlank())
                .ifPresent(v -> context.logWarning(this, store.optionKey()
                        + " is set but " + store.label() + " Spring Data is not on the classpath; the option will be ignored"));
    }

    private static TypeSpec buildReadConverter(ValidatedGeneratorElement element) {
        TypeMirror type = element.element().asType();
        // box() returns a reference type unchanged, so no primitive branch is needed here.
        TypeName payloadTypeName = TypeName.get(element.payloadType()).box();
        TypeName elementTypeName = TypeName.get(type);

        MethodSpec convertMethod = MethodSpec.methodBuilder("convert")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .returns(elementTypeName)
                .addParameter(payloadTypeName, "source")
                .addStatement("return $L", code(element.java().create("source")))
                .build();

        return TypeSpec.classBuilder(element.name().flatName() + "ReadConverter")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .addAnnotation(READING_CONVERTER)
                .addSuperinterface(ParameterizedTypeName.get(CONVERTER, payloadTypeName, elementTypeName))
                .addMethod(convertMethod)
                .build();
    }

    private static TypeSpec buildWriteConverter(ValidatedGeneratorElement element) {
        TypeMirror type = element.element().asType();
        // box() returns a reference type unchanged, so no primitive branch is needed here.
        TypeName payloadTypeName = TypeName.get(element.payloadType()).box();
        TypeName elementTypeName = TypeName.get(type);

        MethodSpec convertMethod = MethodSpec.methodBuilder("convert")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .returns(payloadTypeName)
                .addParameter(elementTypeName, "source")
                .addStatement("return $L", code(element.java().read("source")))
                .build();

        return TypeSpec.classBuilder(element.name().flatName() + "WriteConverter")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .addAnnotation(WRITING_CONVERTER)
                .addSuperinterface(ParameterizedTypeName.get(CONVERTER, elementTypeName, payloadTypeName))
                .addMethod(convertMethod)
                .build();
    }

    private static TypeSpec buildSpringDataConfiguration(List<TypeSpec> converterSpecs,
                                                         Map<SpringDataStore, List<String>> userConverters,
                                                         boolean hasConditionalOnMissingBean) {

        TypeSpec.Builder configBuilder = TypeSpec.classBuilder("LazyvalSpringDataConfiguration")
                .addAnnotation(GeneratedStamp.forGenerator(SpringDataGenerator.class))
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

        userConverters.forEach((store, userFqns) -> configBuilder.addMethod(
                buildBeanMethod(store, converterSpecs, userFqns, hasConditionalOnMissingBean)));

        for (TypeSpec converterSpec : converterSpecs) {
            configBuilder.addType(converterSpec);
        }

        return configBuilder.build();
    }

    private static MethodSpec buildBeanMethod(SpringDataStore store, List<TypeSpec> generated,
                                              List<String> userFqns, boolean hasConditionalOnMissingBean) {
        MethodSpec.Builder beanMethod = MethodSpec.methodBuilder(store.beanName())
                .addAnnotation(BEAN)
                .addModifiers(Modifier.PUBLIC)
                .returns(store.conversionsType());

        if (hasConditionalOnMissingBean) {
            beanMethod.addAnnotation(ClassName.get(
                    "org.springframework.boot.autoconfigure.condition", "ConditionalOnMissingBean"));
        }

        store.construction().parameters().forEach(beanMethod::addParameter);
        addConverterListStatement(beanMethod, generated, userFqns, store.optionKey());
        store.construction().addReturnStatement(beanMethod, store.conversionsType());

        return beanMethod.build();
    }

    private static void addConverterListStatement(MethodSpec.Builder beanMethod, List<TypeSpec> generated,
                                                  List<String> userFqns, String userOptionKey) {
        ClassName listClass = ClassName.get("java.util", "List");
        TypeName converterWildcard = ParameterizedTypeName.get(CONVERTER,
                WildcardTypeName.subtypeOf(Object.class), WildcardTypeName.subtypeOf(Object.class));

        StringBuilder listInit = new StringBuilder("$T<$T> converters = $T.of(\n");
        for (int i = 0; i < generated.size(); i++) {
            listInit.append("    new ").append(generated.get(i).name()).append("()");
            boolean trailingComma = (i < generated.size() - 1) || !userFqns.isEmpty();
            if (trailingComma) {
                listInit.append(",");
            }
            listInit.append("\n");
        }
        if (!userFqns.isEmpty()) {
            listInit.append("    // user-supplied via ").append(userOptionKey).append(":\n");
            for (int i = 0; i < userFqns.size(); i++) {
                listInit.append("    new ").append(userFqns.get(i)).append("()");
                if (i < userFqns.size() - 1) {
                    listInit.append(",");
                }
                listInit.append("\n");
            }
        }
        listInit.append(")");

        beanMethod.addStatement(listInit.toString(), listClass, converterWildcard, listClass);
    }
}
