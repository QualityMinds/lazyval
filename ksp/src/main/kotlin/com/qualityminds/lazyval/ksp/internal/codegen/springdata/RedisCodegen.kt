package com.qualityminds.lazyval.ksp.internal.codegen.springdata

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.internal.codegen.constantName
import com.qualityminds.lazyval.ksp.internal.codegen.kotlinPoet
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

// ── Redis ───────────────────────────────────────────────────────────────────────────────────────
// Redis stores bytes only, so besides the repository converters it gets a public API of its own:
// a RedisSerializer per domain-primitive for templates and cache values, and a cache-key converter.
// The serializers define the bytes; everything else delegates to them.

private const val REDIS_PAYLOAD = "REDIS_PAYLOAD"
private const val REDIS_SERIALIZERS = "RedisSerializers"
private const val REDIS_CACHE_KEYS = "RedisCacheKeys"

/**
 * Stands in for the payload while the factory call is built, then gets replaced by the conversion.
 * `#` cannot occur in a Kotlin identifier, so it never collides with a factory or type name.
 */
private const val PAYLOAD_PLACEHOLDER = "#payload#"

private val CONVERSION_SERVICE = ClassName("org.springframework.core.convert", "ConversionService")
private val GENERIC_CONVERSION_SERVICE = ClassName("org.springframework.core.convert.support", "GenericConversionService")
private val CONVERTER_REGISTRY = ClassName("org.springframework.core.convert.converter", "ConverterRegistry")
private val REDIS_SERIALIZER = ClassName("org.springframework.data.redis.serializer", "RedisSerializer")
private val REDIS_MAPPING_CONTEXT = ClassName("org.springframework.data.redis.core.mapping", "RedisMappingContext")
private val BEAN_POST_PROCESSOR = ClassName("org.springframework.beans.factory.config", "BeanPostProcessor")
private val OBJECT_PROVIDER = ClassName("org.springframework.beans.factory", "ObjectProvider")
private val CONVERTER = ClassName("org.springframework.core.convert.converter", "Converter")
private val BEAN = ClassName("org.springframework.context.annotation", "Bean")

/** Kotlin types whose `::class.java` is the primitive class, which a conversion target must not be. */
private val PRIMITIVES = setOf(BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE)

/**
 * A conversion service holding only Spring Data Redis' default store converters, which turn the
 * payload into bytes and back. File-private, like the converters using it.
 */
internal fun buildRedisPayload(): PropertySpec =
    PropertySpec.builder(REDIS_PAYLOAD, CONVERSION_SERVICE, KModifier.PRIVATE)
        .initializer(
            CodeBlock.builder()
                .beginControlFlow("%T().also", GENERIC_CONVERSION_SERVICE)
                .add("// registers the default store-converters of RedisCustomConversions in the ConversionService\n")
                .add("// which will be used to convert the wrapped values\n")
                .addStatement("%T().registerConvertersIn(it)", SpringDataStore.REDIS.conversionsType)
                .endControlFlow()
                .build()
        )
        .build()

/**
 * The companion holding the `BeanPostProcessor` that hands the conversions' simple types to the
 * `RedisMappingContext`.
 *
 * The repository infrastructure builds that context with Spring Data Redis' default simple types only.
 * `PathIndexResolver` asks the context whether a property is an entity, so without this a
 * domain-primitive counts as one: `@Indexed` on it is ignored and derived queries find nothing,
 * although the hash itself is written through the converters. The other stores wire this already.
 *
 * In a companion with `@JvmStatic`, since post-processors are created before the configuration
 * instance; the conversions are looked up lazily for the same reason.
 */
internal fun buildRedisCompanion(): TypeSpec {
    val postProcess = FunSpec.builder("postProcessBeforeInitialization")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("bean", ANY)
        .addParameter("beanName", STRING)
        .returns(ANY.copy(nullable = true))
        .beginControlFlow("if (bean is %T)", REDIS_MAPPING_CONTEXT)
        .addComment("before afterPropertiesSet(): no entity has been built yet")
        .addStatement("conversions.ifAvailable { bean.setSimpleTypeHolder(it.simpleTypeHolder) }")
        .endControlFlow()
        .addStatement("return bean")
        .build()

    val postProcessor = TypeSpec.anonymousClassBuilder()
        .addSuperinterface(BEAN_POST_PROCESSOR)
        .addFunction(postProcess)
        .build()

    val beanMethod = FunSpec.builder("lazyvalRedisSimpleTypes")
        .addKdoc("""
            Registers the converted domain-primitives as simple types with the `RedisMappingContext`,
            which Spring Data Redis builds without them. Otherwise they count as entities and
            `@Indexed` on them creates no index.
        """.trimIndent())
        .addAnnotation(BEAN)
        .addAnnotation(JvmStatic::class)
        .addParameter("conversions", OBJECT_PROVIDER.parameterizedBy(SpringDataStore.REDIS.conversionsType))
        .returns(BEAN_POST_PROCESSOR)
        .addStatement("return %L", postProcessor)
        .build()

    return TypeSpec.companionObjectBuilder().addFunction(beanMethod).build()
}

/**
 * Public `RedisSerializer` per domain-primitive, for `RedisTemplate` and cache values. `@JvmField`, so
 * Java reads them as plain static fields.
 *
 * Nullability follows Spring Data Redis: `serialize` takes a nullable value but never returns `null` —
 * an empty array stands for it, as in `StringRedisSerializer` — and `deserialize` may return `null`.
 */
internal fun buildRedisSerializers(elements: NonEmptySet<ValidatedKspGeneratorElement>): TypeSpec {
    val holder = TypeSpec.objectBuilder(REDIS_SERIALIZERS)
        .addKdoc("""
            `RedisSerializer`s for `RedisTemplate`, `ReactiveRedisTemplate` and cache values.

            A domain-primitive is stored as its wrapped value, converted by Spring Data Redis' default
            store converters — the same bytes the repositories write.
        """.trimIndent())
    for (element in elements) {
        val elementType = element.element.toClassName()
        val serializerType = REDIS_SERIALIZER.parameterizedBy(elementType)
        val payload = CodeBlock.of("%N.convert(bytes, %L)", REDIS_PAYLOAD, payloadClassLiteral(element))

        val serialize = FunSpec.builder("serialize")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("value", elementType.copy(nullable = true))
            .returns(BYTE_ARRAY)
            .addStatement(
                "return %N.convert(%L, %T::class.java) ?: %T(0)",
                REDIS_PAYLOAD, element.kotlin.readOrNull("value").kotlinPoet(), BYTE_ARRAY, BYTE_ARRAY
            )
            .build()
        val deserialize = FunSpec.builder("deserialize")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("bytes", BYTE_ARRAY.copy(nullable = true))
            .returns(elementType.copy(nullable = true))
            .addStatement("return %L", element.kotlin.createOrNull(PAYLOAD_PLACEHOLDER).kotlinPoet(PAYLOAD_PLACEHOLDER, payload))
            .build()
        val targetType = FunSpec.builder("getTargetType")
            .addModifiers(KModifier.OVERRIDE)
            .returns(Class::class.asClassName().parameterizedBy(STAR))
            .addStatement("return %T::class.java", elementType)
            .build()

        val serializer = TypeSpec.anonymousClassBuilder()
            .addSuperinterface(serializerType)
            .addFunction(serialize)
            .addFunction(deserialize)
            .addFunction(targetType)
            .build()
        holder.addProperty(
            PropertySpec.builder(constantName(element), serializerType)
                .addAnnotation(JvmField::class)
                .initializer("%L", serializer)
                .build()
        )
    }
    return holder.build()
}

/**
 * Public cache-key converter per domain-primitive, so a key reads `cache::978-…` rather than whatever
 * `toString()` returns. The text is the serializer's bytes as UTF-8, i.e. the same representation Redis
 * holds for the value.
 *
 * `object` expressions rather than lambdas: `addConverter` resolves source and target type from the
 * converter's class, which a SAM-converted lambda does not carry.
 */
internal fun buildRedisCacheKeys(
    elements: NonEmptySet<ValidatedKspGeneratorElement>,
    serializers: ClassName,
): TypeSpec {
    val holder = TypeSpec.objectBuilder(REDIS_CACHE_KEYS)
        .addKdoc("""
            Cache-key converters for `RedisCacheConfiguration.addCacheKeyConverter`.

            A domain-primitive used as cache key is rendered as its wrapped value instead of its `toString()`.
        """.trimIndent())
    val registerAll = FunSpec.builder("registerAll")
        .addKdoc("""
            Registers every cache-key converter, e.g.
            `configuration.configureKeyConverters(RedisCacheKeys::registerAll)`.

            A converter is only consulted for keys of its type, so registering all of them is harmless.
        """.trimIndent())
        .addAnnotation(JvmStatic::class)
        .addParameter("registry", CONVERTER_REGISTRY)

    for (element in elements) {
        val elementType = element.element.toClassName()
        val converterType = CONVERTER.parameterizedBy(elementType, STRING)

        val convert = FunSpec.builder("convert")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("source", elementType)
            .returns(STRING)
            .addStatement("return %T.%N.serialize(source).decodeToString()", serializers, constantName(element))
            .build()
        val converter = TypeSpec.anonymousClassBuilder()
            .addSuperinterface(converterType)
            .addFunction(convert)
            .build()
        holder.addProperty(
            PropertySpec.builder(constantName(element), converterType)
                .addAnnotation(JvmField::class)
                .initializer("%L", converter)
                .build()
        )
        registerAll.addStatement("registry.addConverter(%N)", constantName(element))
    }
    return holder.addFunction(registerAll.build()).build()
}

/** The `ByteArray` read converter; delegates to the serializer, so both read the same bytes. */
internal fun buildRedisReadConverter(element: ValidatedKspGeneratorElement, serializers: ClassName): TypeSpec {
    // nullable whatever the factory: the serializer yields null for bytes the store converters cannot
    // read (empty ones), and Spring 7 carries Converter's nullness in the type argument
    val elementType = element.element.toClassName().copy(nullable = true)

    // the "Redis" infix keeps them apart from the payload converters other stores share
    return TypeSpec.classBuilder("${element.name.flatName()}RedisReadConverter")
        .addModifiers(KModifier.PRIVATE)
        .addAnnotation(ClassName("org.springframework.data.convert", "ReadingConverter"))
        .addSuperinterface(CONVERTER.parameterizedBy(BYTE_ARRAY, elementType))
        .addFunction(
            FunSpec.builder("convert")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("source", BYTE_ARRAY)
                .returns(elementType)
                .addStatement("return %T.%N.deserialize(source)", serializers, constantName(element))
                .build()
        )
        .build()
}

/** The `ByteArray` write converter; delegates to the serializer, so both write the same bytes. */
internal fun buildRedisWriteConverter(element: ValidatedKspGeneratorElement, serializers: ClassName): TypeSpec {
    val elementType = element.element.toClassName()

    return TypeSpec.classBuilder("${element.name.flatName()}RedisWriteConverter")
        .addModifiers(KModifier.PRIVATE)
        .addAnnotation(ClassName("org.springframework.data.convert", "WritingConverter"))
        .addSuperinterface(CONVERTER.parameterizedBy(elementType, BYTE_ARRAY))
        .addFunction(
            FunSpec.builder("convert")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("source", elementType)
                .returns(BYTE_ARRAY)
                .addStatement("return %T.%N.serialize(source)", serializers, constantName(element))
                .build()
        )
        .build()
}

/** The nested holder classes, as seen from the file's top level. */
internal fun redisSerializersOf(configuration: ClassName): ClassName = configuration.nestedClass(REDIS_SERIALIZERS)

/**
 * The class to convert the payload to. Boxed through `javaObjectType` for primitives — `Int::class.java`
 * is `int`, not `Integer` — and raw, since a class literal cannot be parameterized.
 */
private fun payloadClassLiteral(element: ValidatedKspGeneratorElement): CodeBlock {
    val payload = element.payloadType.toTypeName().copy(nullable = false)
    val raw = if (payload is ParameterizedTypeName) payload.rawType else payload
    return if (raw in PRIMITIVES) CodeBlock.of("%T::class.javaObjectType", raw) else CodeBlock.of("%T::class.java", raw)
}
