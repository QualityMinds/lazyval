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
import java.util.stream.Stream;

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
 * <b>Write converters:</b> the wrapped type inside a domain-primitive is always non-nullable
 * (lazyval rejects nullable wrapped properties at compile time), so write converters always
 * return a non-null value.
 */
// must only be public for ServiceLoader, but it is not part of the API
public class SpringDataGenerator implements Generator {

    private static final String OPTION_GENERATED_PACKAGE = "lazyval.springdata.package";
    private static final String OPTION_CASSANDRA_CONVERTERS = "lazyval.springdata.cassandra.converters";
    private static final String OPTION_MONGO_CONVERTERS = "lazyval.springdata.mongo.converters";
    private static final String OPTION_JDBC_CONVERTERS = "lazyval.springdata.jdbc.converters";
    private static final String OPTION_R2DBC_CONVERTERS = "lazyval.springdata.r2dbc.converters";

    private static final String CASSANDRA_CUSTOM_CONVERSIONS_FQN = "org.springframework.data.cassandra.core.convert.CassandraCustomConversions";
    private static final String MONGO_CUSTOM_CONVERSIONS_FQN = "org.springframework.data.mongodb.core.convert.MongoCustomConversions";
    private static final String JDBC_CUSTOM_CONVERSIONS_FQN = "org.springframework.data.jdbc.core.convert.JdbcCustomConversions";
    private static final String R2DBC_CUSTOM_CONVERSIONS_FQN = "org.springframework.data.r2dbc.convert.R2dbcCustomConversions";
    private static final String CONVERTER_FQN = "org.springframework.core.convert.converter.Converter";
    private static final String READING_CONVERTER_FQN = "org.springframework.data.convert.ReadingConverter";
    private static final String WRITING_CONVERTER_FQN = "org.springframework.data.convert.WritingConverter";

    private static final ClassName READING_CONVERTER = ClassName.get("org.springframework.data.convert", "ReadingConverter");
    private static final ClassName WRITING_CONVERTER = ClassName.get("org.springframework.data.convert", "WritingConverter");
    private static final ClassName CONVERTER = ClassName.get("org.springframework.core.convert.converter", "Converter");
    private static final ClassName CONFIGURATION = ClassName.get("org.springframework.context.annotation", "Configuration");
    private static final ClassName BEAN = ClassName.get("org.springframework.context.annotation", "Bean");
    private static final ClassName CASSANDRA_CUSTOM_CONVERSIONS = ClassName.get(
            "org.springframework.data.cassandra.core.convert", "CassandraCustomConversions");
    private static final ClassName MONGO_CUSTOM_CONVERSIONS = ClassName.get(
            "org.springframework.data.mongodb.core.convert", "MongoCustomConversions");
    private static final ClassName JDBC_CUSTOM_CONVERSIONS = ClassName.get(
            "org.springframework.data.jdbc.core.convert", "JdbcCustomConversions");
    private static final ClassName R2DBC_CUSTOM_CONVERSIONS = ClassName.get(
            "org.springframework.data.r2dbc.convert", "R2dbcCustomConversions");
    private static final ClassName R2DBC_DIALECT_RESOLVER = ClassName.get(
            "org.springframework.data.r2dbc.dialect", "DialectResolver");
    private static final ClassName CONNECTION_FACTORY = ClassName.get("io.r2dbc.spi", "ConnectionFactory");
    private static final ClassName JDBC_DIALECT = ClassName.get(
            "org.springframework.data.jdbc.core.dialect", "JdbcDialect");

    private static final AnnotationSpec OVERRIDE_ANNOTATION = AnnotationSpec.builder(
            ClassName.get("java.lang", "Override")).build();

    @Override
    public String generatorId() {
        return StockGeneratorIds.SPRING_DATA;
    }

    @Override
    public Set<String> requiredClasspath() {
        return Set.of("org.springframework.data.convert.ReadingConverter");
    }

    @Override
    public Set<String> supportedOptions() {
        return Set.of(OPTION_GENERATED_PACKAGE, OPTION_CASSANDRA_CONVERTERS, OPTION_MONGO_CONVERTERS,
                OPTION_JDBC_CONVERTERS, OPTION_R2DBC_CONVERTERS);
    }

    @Override
    public Set<String> supersedes() {
        return Set.of(StockGeneratorIds.CASSANDRA_CODEC, StockGeneratorIds.MONGODB_CODEC);
    }

    @Override
    public Stream<GeneratorResult> generate(NonEmptySet<ValidatedGeneratorElement> elements, Context context) {
        boolean isCassandra = context.isOnClasspath(CASSANDRA_CUSTOM_CONVERSIONS_FQN);
        boolean isMongo = context.isOnClasspath(MONGO_CUSTOM_CONVERSIONS_FQN);
        boolean isJdbc = context.isOnClasspath(JDBC_CUSTOM_CONVERSIONS_FQN);
        boolean isR2dbc = context.isOnClasspath(R2DBC_CUSTOM_CONVERSIONS_FQN);

        if (!isCassandra && !isMongo && !isJdbc && !isR2dbc) {
            return Stream.empty();
        }

        final String converterPackage = context.generatorPackage(OPTION_GENERATED_PACKAGE, "boundary.persistence");

        List<String> cassandraUserFqns = isCassandra
                ? validateUserConverters(OPTION_CASSANDRA_CONVERTERS, context, converterPackage)
                : warnIfOptionSetForMissingStorage(OPTION_CASSANDRA_CONVERTERS, "Cassandra", context);
        List<String> mongoUserFqns = isMongo
                ? validateUserConverters(OPTION_MONGO_CONVERTERS, context, converterPackage)
                : warnIfOptionSetForMissingStorage(OPTION_MONGO_CONVERTERS, "MongoDB", context);
        List<String> jdbcUserFqns = isJdbc
                ? validateUserConverters(OPTION_JDBC_CONVERTERS, context, converterPackage)
                : warnIfOptionSetForMissingStorage(OPTION_JDBC_CONVERTERS, "JDBC", context);
        List<String> r2dbcUserFqns = isR2dbc
                ? validateUserConverters(OPTION_R2DBC_CONVERTERS, context, converterPackage)
                : warnIfOptionSetForMissingStorage(OPTION_R2DBC_CONVERTERS, "R2DBC", context);

        boolean hasConditionalOnMissingBean = context.isOnClasspath(
                "org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean");

        List<TypeSpec> converterSpecs = new ArrayList<>();
        for (ValidatedGeneratorElement element : elements) {
            converterSpecs.add(buildReadConverter(element));
            converterSpecs.add(buildWriteConverter(element));
        }

        TypeSpec configSpec = buildSpringDataConfiguration(
                converterSpecs, isCassandra, isMongo, isJdbc, isR2dbc,
                cassandraUserFqns, mongoUserFqns, jdbcUserFqns, r2dbcUserFqns, hasConditionalOnMissingBean);
        JavaFile configFile = JavaFile.builder(converterPackage, configSpec)
                .skipJavaLangImports(true)
                .build();

        return Stream.of(new GeneratorResult.Java(
                new GeneratorResult.Metadata(configFile.packageName(), configFile.typeSpec().name()),
                configFile.toString()));
    }

    private List<String> validateUserConverters(String optionKey, Context context, String converterPackage) {
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

    private List<String> warnIfOptionSetForMissingStorage(String optionKey, String storageLabel, Context context) {
        context.getSetting(optionKey)
                .filter(v -> !v.isBlank())
                .ifPresent(v -> context.logWarning(this,
                        optionKey + " is set but " + storageLabel + " Spring Data is not on the classpath; the option will be ignored"));
        return List.of();
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

        return TypeSpec.classBuilder(element.typeName().name() + "ReadConverter")
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

        return TypeSpec.classBuilder(element.typeName().name() + "WriteConverter")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .addAnnotation(WRITING_CONVERTER)
                .addSuperinterface(ParameterizedTypeName.get(CONVERTER, elementTypeName, wrappedTypeName))
                .addMethod(convertMethod)
                .build();
    }

    private static TypeSpec buildSpringDataConfiguration(List<TypeSpec> converterSpecs,
                                                         boolean isCassandra, boolean isMongo,
                                                         boolean isJdbc, boolean isR2dbc,
                                                         List<String> cassandraUserFqns, List<String> mongoUserFqns,
                                                         List<String> jdbcUserFqns, List<String> r2dbcUserFqns,
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

        if (isCassandra) {
            configBuilder.addMethod(buildBeanMethod("cassandraCustomConversions", CASSANDRA_CUSTOM_CONVERSIONS,
                    converterSpecs, cassandraUserFqns, OPTION_CASSANDRA_CONVERTERS, hasConditionalOnMissingBean));
        }
        if (isMongo) {
            configBuilder.addMethod(buildBeanMethod("mongoCustomConversions", MONGO_CUSTOM_CONVERSIONS,
                    converterSpecs, mongoUserFqns, OPTION_MONGO_CONVERTERS, hasConditionalOnMissingBean));
        }
        if (isJdbc) {
            configBuilder.addMethod(buildDialectAwareBeanMethod("jdbcCustomConversions",
                    JDBC_CUSTOM_CONVERSIONS, JDBC_DIALECT, "dialect", CodeBlock.of("dialect"),
                    converterSpecs, jdbcUserFqns, OPTION_JDBC_CONVERTERS, hasConditionalOnMissingBean));
        }
        if (isR2dbc) {
            configBuilder.addMethod(buildDialectAwareBeanMethod("r2dbcCustomConversions",
                    R2DBC_CUSTOM_CONVERSIONS, CONNECTION_FACTORY, "connectionFactory",
                    CodeBlock.of("$T.getDialect(connectionFactory)", R2DBC_DIALECT_RESOLVER),
                    converterSpecs, r2dbcUserFqns, OPTION_R2DBC_CONVERTERS, hasConditionalOnMissingBean));
        }
        for (TypeSpec converterSpec : converterSpecs) {
            configBuilder.addType(converterSpec);
        }

        return configBuilder.build();
    }

    private static MethodSpec buildBeanMethod(String methodName, ClassName conversionsType,
                                              List<TypeSpec> generated, List<String> userFqns,
                                              String userOptionKey, boolean hasConditionalOnMissingBean) {
        ClassName listClass = ClassName.get("java.util", "List");
        TypeName converterWildcard = ParameterizedTypeName.get(CONVERTER,
                WildcardTypeName.subtypeOf(Object.class), WildcardTypeName.subtypeOf(Object.class));

        MethodSpec.Builder beanMethod = MethodSpec.methodBuilder(methodName)
                .addAnnotation(BEAN)
                .addModifiers(Modifier.PUBLIC)
                .returns(conversionsType);

        if (hasConditionalOnMissingBean) {
            beanMethod.addAnnotation(ClassName.get(
                    "org.springframework.boot.autoconfigure.condition", "ConditionalOnMissingBean"));
        }

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
        beanMethod.addStatement("return new $T(converters)", conversionsType);

        return beanMethod.build();
    }

    /**
     * Bean method for the relational stores, whose {@code CustomConversions} need a
     * {@code Dialect}: it contributes the store's own simple types and converters, and neither
     * {@code JdbcCustomConversions} nor {@code R2dbcCustomConversions} picks those up from a plain
     * {@code Collection} constructor. Using one avoids silently dropping them — Spring Data's own
     * configuration builds these beans the same way.
     * <p>
     * How the dialect is obtained differs per store:
     * <ul>
     *   <li><b>JDBC</b> takes the {@code JdbcDialect} bean directly; Spring Boot publishes one from
     *       {@code DataJdbcRepositoriesAutoConfiguration}.</li>
     *   <li><b>R2DBC</b> resolves it from the {@code ConnectionFactory}, because Spring Boot
     *       publishes no {@code R2dbcDialect} bean — {@code DataR2dbcAutoConfiguration} resolves it
     *       in its own constructor and keeps it in a private field. Taking an {@code R2dbcDialect}
     *       parameter compiles, but leaves the bean unsatisfiable at runtime.</li>
     * </ul>
     */
    private static MethodSpec buildDialectAwareBeanMethod(String methodName, ClassName conversionsType,
                                                          ClassName parameterType, String parameterName,
                                                          CodeBlock dialectExpression,
                                                          List<TypeSpec> generated, List<String> userFqns,
                                                          String userOptionKey,
                                                          boolean hasConditionalOnMissingBean) {
        MethodSpec.Builder beanMethod = MethodSpec.methodBuilder(methodName)
                .addAnnotation(BEAN)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(parameterType, parameterName)
                .returns(conversionsType);

        if (hasConditionalOnMissingBean) {
            beanMethod.addAnnotation(ClassName.get(
                    "org.springframework.boot.autoconfigure.condition", "ConditionalOnMissingBean"));
        }

        addConverterListStatement(beanMethod, generated, userFqns, userOptionKey);
        beanMethod.addStatement("return $T.of($L, converters)", conversionsType, dialectExpression);

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
