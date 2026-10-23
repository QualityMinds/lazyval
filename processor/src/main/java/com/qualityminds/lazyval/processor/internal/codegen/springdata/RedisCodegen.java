package com.qualityminds.lazyval.processor.internal.codegen.springdata;

import com.palantir.javapoet.*;
import com.qualityminds.lazyval.collections.NonEmptySet;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.Modifier;

import com.qualityminds.lazyval.processor.internal.codegen.ConstantNames;

import static com.qualityminds.lazyval.processor.internal.codegen.JavaPoetExprs.code;

/**
 * The Redis part of {@link SpringDataGenerator}'s output.
 * <p>
 * Redis stores bytes only, so besides the repository converters it gets a public API of its own: a
 * {@code RedisSerializer} per domain-primitive for templates and cache values, and a cache-key
 * converter. The serializers define the bytes; everything else delegates to them.
 */
final class RedisCodegen {

    private static final ClassName READING_CONVERTER = ClassName.get("org.springframework.data.convert", "ReadingConverter");
    private static final ClassName WRITING_CONVERTER = ClassName.get("org.springframework.data.convert", "WritingConverter");
    private static final ClassName CONVERTER = ClassName.get("org.springframework.core.convert.converter", "Converter");
    private static final ClassName BEAN = ClassName.get("org.springframework.context.annotation", "Bean");
    private static final AnnotationSpec OVERRIDE_ANNOTATION = AnnotationSpec.builder(
            ClassName.get("java.lang", "Override")).build();

    private static final ClassName CONVERSION_SERVICE = ClassName.get("org.springframework.core.convert", "ConversionService");
    private static final ClassName GENERIC_CONVERSION_SERVICE = ClassName.get("org.springframework.core.convert.support", "GenericConversionService");
    private static final ClassName CONVERTER_REGISTRY = ClassName.get("org.springframework.core.convert.converter", "ConverterRegistry");
    private static final ClassName REDIS_SERIALIZER = ClassName.get("org.springframework.data.redis.serializer", "RedisSerializer");
    private static final ClassName REDIS_MAPPING_CONTEXT = ClassName.get("org.springframework.data.redis.core.mapping", "RedisMappingContext");
    private static final ClassName BEAN_POST_PROCESSOR = ClassName.get("org.springframework.beans.factory.config", "BeanPostProcessor");
    private static final ClassName OBJECT_PROVIDER = ClassName.get("org.springframework.beans.factory", "ObjectProvider");
    private static final ClassName STANDARD_CHARSETS = ClassName.get("java.nio.charset", "StandardCharsets");
    private static final TypeName BYTES = ArrayTypeName.of(TypeName.BYTE);

    private static final String REDIS_SERIALIZERS = "RedisSerializers";
    private static final String REDIS_CACHE_KEYS = "RedisCacheKeys";
    private static final String REDIS_PAYLOAD_METHOD = "redisPayloadConversions";
    private static final FieldSpec REDIS_PAYLOAD = FieldSpec.builder(CONVERSION_SERVICE, "REDIS_PAYLOAD",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("$N()", REDIS_PAYLOAD_METHOD)
            .build();
    /**
     * Stands in for the payload while the factory call is built, then gets replaced by the conversion.
     * {@code #} cannot occur in a Java identifier, so it never collides with a factory or type name.
     */
    private static final String PAYLOAD_PLACEHOLDER = "#payload#";

    private RedisCodegen() {
    }

    /**
     * {@code REDIS_PAYLOAD} and the method initializing it: a conversion service holding only Spring
     * Data Redis' default store converters, which turn the payload into bytes and back.
     */
    static void addRedisPayloadConversions(TypeSpec.Builder configBuilder) {
        configBuilder.addField(REDIS_PAYLOAD);
        configBuilder.addMethod(MethodSpec.methodBuilder(REDIS_PAYLOAD_METHOD)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(CONVERSION_SERVICE)
                .addStatement("$T cs = new $T()", GENERIC_CONVERSION_SERVICE, GENERIC_CONVERSION_SERVICE)
                .addComment("this registers the default store-converters from RedisCustomConversions in the ConversionService")
                .addComment("which will be used to convert the wrapped values")
                .addStatement("new $T().registerConvertersIn(cs)", SpringDataStore.REDIS.conversionsType())
                .addStatement("return cs")
                .build());
    }

    /**
     * A {@code BeanPostProcessor} handing the conversions' simple types to the {@code RedisMappingContext}.
     * <p>
     * The repository infrastructure builds that context with Spring Data Redis' default simple types only.
     * {@code PathIndexResolver} asks the context whether a property is an entity, so without this a
     * domain-primitive counts as one: {@code @Indexed} on it is ignored and derived queries find nothing,
     * although the hash itself is written through the converters. The other stores wire this already.
     * <p>
     * {@code static}, since post-processors are created before the configuration instance; the
     * conversions are looked up lazily for the same reason.
     */
    static MethodSpec buildRedisSimpleTypesPostProcessor() {
        ParameterizedTypeName conversionsProvider = ParameterizedTypeName.get(OBJECT_PROVIDER,
                SpringDataStore.REDIS.conversionsType());

        MethodSpec postProcess = MethodSpec.methodBuilder("postProcessBeforeInitialization")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .returns(Object.class)
                .addParameter(Object.class, "bean")
                .addParameter(String.class, "beanName")
                .beginControlFlow("if (bean instanceof $T context)", REDIS_MAPPING_CONTEXT)
                .addComment("before afterPropertiesSet(): no entity has been built yet")
                .addStatement("conversions.ifAvailable(c -> context.setSimpleTypeHolder(c.getSimpleTypeHolder()))")
                .endControlFlow()
                .addStatement("return bean")
                .build();

        TypeSpec postProcessor = TypeSpec.anonymousClassBuilder("")
                .addSuperinterface(BEAN_POST_PROCESSOR)
                .addMethod(postProcess)
                .build();

        return MethodSpec.methodBuilder("lazyvalRedisSimpleTypes")
                .addJavadoc("""
                        Registers the converted domain-primitives as simple types with the {@code RedisMappingContext},
                        which Spring Data Redis builds without them. Otherwise they count as entities and
                        {@code @Indexed} on them creates no index.
                        """)
                .addAnnotation(BEAN)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(BEAN_POST_PROCESSOR)
                .addParameter(conversionsProvider, "conversions")
                .addStatement("return $L", postProcessor)
                .build();
    }

    /**
     * Public {@code RedisSerializer} per domain-primitive, for {@code RedisTemplate} and cache values.
     * They define the bytes: the repository converters delegate to them, so a value written through a
     * repository reads back through a template and vice versa.
     */
    static TypeSpec buildRedisSerializers(NonEmptySet<ValidatedGeneratorElement> elements) {
        TypeSpec.Builder holder = constantHolder(REDIS_SERIALIZERS, """
                {@code RedisSerializer}s for {@code RedisTemplate}, {@code ReactiveRedisTemplate} and cache values.
                <p>
                A domain-primitive is stored as its wrapped value, converted by Spring Data Redis' default
                store converters — the same bytes the repositories write.
                """);
        for (ValidatedGeneratorElement element : elements) {
            TypeName elementTypeName = TypeName.get(element.element().asType());
            CodeBlock payload = CodeBlock.of("$N.convert(bytes, $T.class)", REDIS_PAYLOAD, payloadClass(element));

            MethodSpec serialize = MethodSpec.methodBuilder("serialize")
                    .addAnnotation(OVERRIDE_ANNOTATION)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(BYTES)
                    .addParameter(elementTypeName, "value")
                    // never null: Spring Data Redis declares a non-null return, and an empty array
                    // stands for null, as in StringRedisSerializer
                    .addStatement("return value == null ? new byte[0] : $N.convert($L, $T.class)",
                            REDIS_PAYLOAD, code(element.java().read("value")), BYTES)
                    .build();
            MethodSpec deserialize = MethodSpec.methodBuilder("deserialize")
                    .addAnnotation(OVERRIDE_ANNOTATION)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(elementTypeName)
                    .addParameter(BYTES, "bytes")
                    .addStatement("return bytes == null ? null : $L",
                            code(element.java().create(PAYLOAD_PLACEHOLDER), PAYLOAD_PLACEHOLDER, payload))
                    .build();
            MethodSpec targetType = MethodSpec.methodBuilder("getTargetType")
                    .addAnnotation(OVERRIDE_ANNOTATION)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(Object.class)))
                    .addStatement("return $T.class", rawType(elementTypeName))
                    .build();

            TypeName serializerType = ParameterizedTypeName.get(REDIS_SERIALIZER, elementTypeName);
            TypeSpec serializer = TypeSpec.anonymousClassBuilder("")
                    .addSuperinterface(serializerType)
                    .addMethod(serialize)
                    .addMethod(deserialize)
                    .addMethod(targetType)
                    .build();
            holder.addField(FieldSpec.builder(serializerType, ConstantNames.of(element),
                            Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$L", serializer)
                    .build());
        }
        return holder.build();
    }

    /**
     * Public cache-key converter per domain-primitive, so a key reads {@code cache::978-…} rather than
     * whatever {@code toString()} returns. The text is the serializer's bytes as UTF-8, i.e. the same
     * representation Redis holds for the value.
     * <p>
     * Anonymous classes rather than lambdas: {@code addCacheKeyConverter} resolves source and target
     * type from the converter's class, which a lambda does not carry.
     */
    static TypeSpec buildRedisCacheKeys(NonEmptySet<ValidatedGeneratorElement> elements) {
        TypeSpec.Builder holder = constantHolder(REDIS_CACHE_KEYS, """
                Cache-key converters for {@code RedisCacheConfiguration#addCacheKeyConverter}.
                <p>
                A domain-primitive used as cache key is rendered as its wrapped value instead of its {@code toString()}.
                """);
        for (ValidatedGeneratorElement element : elements) {
            TypeName elementTypeName = TypeName.get(element.element().asType());
            TypeName converterType = ParameterizedTypeName.get(CONVERTER, elementTypeName, ClassName.get(String.class));

            MethodSpec convert = MethodSpec.methodBuilder("convert")
                    .addAnnotation(OVERRIDE_ANNOTATION)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(String.class)
                    .addParameter(elementTypeName, "source")
                    .addStatement("return new $T($N.$N.serialize(source), $T.UTF_8)",
                            String.class, REDIS_SERIALIZERS, ConstantNames.of(element), STANDARD_CHARSETS)
                    .build();
            TypeSpec converter = TypeSpec.anonymousClassBuilder("")
                    .addSuperinterface(converterType)
                    .addMethod(convert)
                    .build();
            holder.addField(FieldSpec.builder(converterType, ConstantNames.of(element),
                            Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$L", converter)
                    .build());
        }
        holder.addMethod(buildRegisterAll(elements));
        return holder.build();
    }

    /** The {@code byte[]} read converter; delegates to the serializer, so both read the same bytes. */
    static TypeSpec buildRedisReadConverter(ValidatedGeneratorElement element) {
        TypeName elementTypeName = TypeName.get(element.element().asType());

        MethodSpec convertMethod = MethodSpec.methodBuilder("convert")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .returns(elementTypeName)
                .addParameter(BYTES, "source")
                .addStatement("return $N.$N.deserialize(source)", REDIS_SERIALIZERS, ConstantNames.of(element))
                .build();

        // the "Redis" infix keeps them apart from the payload converters other stores share
        return TypeSpec.classBuilder(element.name().flatName() + "RedisReadConverter")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .addAnnotation(READING_CONVERTER)
                .addSuperinterface(ParameterizedTypeName.get(CONVERTER, BYTES, elementTypeName))
                .addMethod(convertMethod)
                .build();
    }

    /** The {@code byte[]} write converter; delegates to the serializer, so both write the same bytes. */
    static TypeSpec buildRedisWriteConverter(ValidatedGeneratorElement element) {
        TypeName elementTypeName = TypeName.get(element.element().asType());

        MethodSpec convertMethod = MethodSpec.methodBuilder("convert")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .returns(BYTES)
                .addParameter(elementTypeName, "source")
                .addStatement("return $N.$N.serialize(source)", REDIS_SERIALIZERS, ConstantNames.of(element))
                .build();

        return TypeSpec.classBuilder(element.name().flatName() + "RedisWriteConverter")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .addAnnotation(WRITING_CONVERTER)
                .addSuperinterface(ParameterizedTypeName.get(CONVERTER, elementTypeName, BYTES))
                .addMethod(convertMethod)
                .build();
    }

    /**
     * Registers every cache-key converter at once, shaped to fit
     * {@code RedisCacheConfiguration#configureKeyConverters}. Only {@code ConverterRegistry} from
     * spring-core is named, so this stays usable without Spring Boot.
     */
    private static MethodSpec buildRegisterAll(NonEmptySet<ValidatedGeneratorElement> elements) {
        MethodSpec.Builder registerAll = MethodSpec.methodBuilder("registerAll")
                .addJavadoc("""
                        Registers every cache-key converter, e.g.
                        {@code configuration.configureKeyConverters(RedisCacheKeys::registerAll)}.
                        <p>
                        A converter is only consulted for keys of its type, so registering all of them is harmless.
                        """)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(CONVERTER_REGISTRY, "registry");
        for (ValidatedGeneratorElement element : elements) {
            registerAll.addStatement("registry.addConverter($N)", ConstantNames.of(element));
        }
        return registerAll.build();
    }

    private static TypeSpec.Builder constantHolder(String name, String javadoc) {
        return TypeSpec.classBuilder(name)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addJavadoc(javadoc)
                .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());
    }

    private static TypeName rawType(TypeName type) {
        return type instanceof ParameterizedTypeName parameterized ? parameterized.rawType() : type;
    }

    /** The class literal to convert the payload to: boxed, and raw since a literal cannot be parameterized. */
    private static TypeName payloadClass(ValidatedGeneratorElement element) {
        return rawType(TypeName.get(element.payloadType()).box());
    }
}
