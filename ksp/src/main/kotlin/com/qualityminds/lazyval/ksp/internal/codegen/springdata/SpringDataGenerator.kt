package com.qualityminds.lazyval.ksp.internal.codegen.springdata

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.internal.codegen.GeneratedStamp.addGeneratedAnnotation
import com.qualityminds.lazyval.ksp.internal.codegen.kotlinPoet
import com.qualityminds.lazyval.ksp.internal.codegen.uniqueConstantNames
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.StockGeneratorIds
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import java.util.stream.Stream

/**
 * Generates Spring Data `Converter` read/write pairs for each domain-primitive and a single
 * `LazyvalSpringDataConfiguration` file that registers them with the store's
 * `CustomConversions` bean.
 *
 * ## Null invariants
 *
 * Spring Data's `Converter` contract guarantees a **non-null** `source` argument for both read
 * and write converters; null column values are resolved by Spring Data before the converter is
 * invoked and never reach `convert`.
 *
 * ### Read converters
 *
 * The `Converter` type parameter and `convert` return type reflect the factory method's
 * declared nullability:
 * - **Non-nullable factory** → `Converter<InnerType, DomainType>`, returns `DomainType`
 * - **Nullable factory** → `Converter<InnerType, DomainType?>`, returns `DomainType?`
 *
 * When a nullable factory returns `null` for a non-null DB value (e.g. a blank-string guard),
 * Spring Data propagates `null` to the target property. The target property **must** be
 * declared as a nullable Kotlin property (`val x: DomainType?`); otherwise a
 * `NullPointerException` will be thrown at first access.
 *
 * ### Write converters
 *
 * The payload inside a domain-primitive is always non-nullable (lazyval rejects nullable
 * payloads at compile time). Write converters therefore always return a non-null
 * value: `Converter<DomainType, InnerType>`.
 */
// File-level rather than in a companion so the emission functions at the bottom of the file can see
// them too — a companion's private members are visible to the class only.
private const val OPTION_GENERATED_PACKAGE = "lazyval.springdata.package"
private const val CONFIGURATION_NAME = "LazyvalSpringDataConfiguration"

private const val CONVERTER_FQN = "org.springframework.core.convert.converter.Converter"
private const val READING_CONVERTER_FQN = "org.springframework.data.convert.ReadingConverter"
private const val WRITING_CONVERTER_FQN = "org.springframework.data.convert.WritingConverter"

private val READING_CONVERTER = ClassName("org.springframework.data.convert", "ReadingConverter")
private val WRITING_CONVERTER = ClassName("org.springframework.data.convert", "WritingConverter")
private val CONVERTER = ClassName("org.springframework.core.convert.converter", "Converter")
private val CONFIGURATION = ClassName("org.springframework.context.annotation", "Configuration")
private val BEAN = ClassName("org.springframework.context.annotation", "Bean")
private val CONDITIONAL_ON_MISSING_BEAN = ClassName(
    "org.springframework.boot.autoconfigure.condition", "ConditionalOnMissingBean"
)

class SpringDataGenerator : Generator {

    override fun generatorId(): String = StockGeneratorIds.SPRING_DATA

    override fun requiredClasspath(): Set<String> =
        setOf(
            "org.springframework.data.convert.ReadingConverter",
            "org.springframework.data.convert.WritingConverter")

    override fun supportedOptions(): Set<String> =
        setOf(OPTION_GENERATED_PACKAGE) + SpringDataStore.entries.map { it.optionKey }

    override fun supersedes(): Set<String> =
        setOf(StockGeneratorIds.CASSANDRA_CODEC, StockGeneratorIds.MONGODB_CODEC, StockGeneratorIds.REDIS_LETTUCE_CODEC)

    override fun generate(
        validatedElements: NonEmptySet<ValidatedKspGeneratorElement>,
        context: Generator.Context
    ): Stream<GeneratorResult> {
        val (activeStores, inactiveStores) = SpringDataStore.entries
            .partition { context.isOnClasspath(it.conversionsFqn) }
        if (activeStores.isEmpty()) {
            return Stream.empty()
        }

        val converterPackage = context.generatorPackage(OPTION_GENERATED_PACKAGE, "boundary.persistence")

        inactiveStores.forEach { warnIfOptionSetForMissingStore(it, context) }
        // LinkedHashMap, so bean methods are emitted in SpringDataStore declaration order
        val userConverters = activeStores.associateWith { validateUserConverters(it, context, converterPackage) }

        val configuration = ClassName(converterPackage, CONFIGURATION_NAME)
        val redis = activeStores.any { it.converterKind == SpringDataStore.ConverterKind.BINARY }
        if (redis && !uniqueConstantNames(validatedElements, context, this)) {
            return Stream.empty()
        }

        // only the kinds an active store needs: a Redis-only build carries no payload converters.
        // Sorted, so payload converters come before the Redis ones whatever the store order.
        val converterSpecs = activeStores.map { it.converterKind }.distinct().sorted()
            .associateWith { buildConverters(it, validatedElements, configuration) }

        val configSpec = buildSpringDataConfiguration(converterSpecs, userConverters, context, redis, validatedElements, configuration)

        val file = FileSpec.builder(converterPackage, CONFIGURATION_NAME)
            .addType(configSpec)
            .apply { if (redis) addProperty(buildRedisPayload()) }
            .apply { converterSpecs.values.flatten().forEach { addType(it) } }
            .build()

        return Stream.of(GeneratorResult.Kotlin(
            GeneratorResult.Metadata(file.packageName, file.name),
            file.toString()
        ))
    }

    private fun validateUserConverters(
        store: SpringDataStore,
        context: Generator.Context,
        converterPackage: String
    ): List<String> {
        val optionKey = store.optionKey
        val raw = context.getSetting(optionKey) ?: return emptyList()
        if (raw.isBlank()) return emptyList()

        val fqns = raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val valid = mutableListOf<String>()
        for (fqn in fqns) {
            val info = context.inspectClass(fqn)
            if (info == null) {
                context.logError(this, "$optionKey: class '$fqn' not found on compile classpath")
                continue
            }
            var ok = true
            if (!info.isAssignableTo(CONVERTER_FQN)) {
                context.logError(this, "$optionKey: class '$fqn' does not implement $CONVERTER_FQN")
                ok = false
            }
            if (!info.isAccessibleFrom(converterPackage)) {
                context.logError(this, "$optionKey: class '$fqn' is not accessible from the generated configuration at package '$converterPackage'")
                ok = false
            }
            if (!info.hasAccessibleNoArgConstructor(converterPackage)) {
                context.logError(this, "$optionKey: class '$fqn' must declare a no-arg constructor accessible from the generated configuration at package '$converterPackage'")
                ok = false
            }
            if (!info.hasAnnotation(READING_CONVERTER_FQN) && !info.hasAnnotation(WRITING_CONVERTER_FQN)) {
                context.logError(this, "$optionKey: class '$fqn' must be annotated with @ReadingConverter or @WritingConverter")
                ok = false
            }
            if (ok) {
                valid += fqn
            }
        }
        return valid
    }

    private fun warnIfOptionSetForMissingStore(store: SpringDataStore, context: Generator.Context) {
        val raw = context.getSetting(store.optionKey)
        if (!raw.isNullOrBlank()) {
            context.logWarning(
                this,
                "${store.optionKey} is set but ${store.label} Spring Data is not on the classpath; the option will be ignored"
            )
        }
    }
}

// ── emission ────────────────────────────────────────────────────────────────────────────────────
// Top-level rather than members: these need no generator state, and keeping them out of the class
// keeps SpringDataGenerator focused on deciding *what* to generate.

private fun buildConverters(
    kind: SpringDataStore.ConverterKind,
    elements: NonEmptySet<ValidatedKspGeneratorElement>,
    configuration: ClassName,
): List<TypeSpec> = elements.flatMap { element ->
    when (kind) {
        SpringDataStore.ConverterKind.PAYLOAD -> listOf(buildReadConverter(element), buildWriteConverter(element))
        SpringDataStore.ConverterKind.BINARY -> {
            val serializers = redisSerializersOf(configuration)
            listOf(buildRedisReadConverter(element, serializers), buildRedisWriteConverter(element, serializers))
        }
    }
}

private fun buildReadConverter(element: ValidatedKspGeneratorElement): TypeSpec {
    val elementClassName = element.element.toClassName()
    val payloadTypeName = element.payloadType.toTypeName()
    val factoryReturnsNullable = element.factoryMethod?.returnType?.resolve()?.isMarkedNullable ?: false
    val returnType = elementClassName.copy(nullable = factoryReturnsNullable)

    return TypeSpec.classBuilder("${element.name.flatName()}ReadConverter")
        .addModifiers(KModifier.PRIVATE)
        .addAnnotation(READING_CONVERTER)
        .addSuperinterface(
            CONVERTER.parameterizedBy(payloadTypeName.copy(nullable = false), returnType)
        )
        .addFunction(
            FunSpec.builder("convert")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("source", payloadTypeName.copy(nullable = false))
                .returns(returnType)
                .addStatement("return %L", element.kotlin.create("source").kotlinPoet())
                .build()
        )
        .build()
}

private fun buildWriteConverter(element: ValidatedKspGeneratorElement): TypeSpec {
    val elementClassName = element.element.toClassName()
    val payloadTypeName = element.payloadType.toTypeName()

    return TypeSpec.classBuilder("${element.name.flatName()}WriteConverter")
        .addModifiers(KModifier.PRIVATE)
        .addAnnotation(WRITING_CONVERTER)
        .addSuperinterface(
            CONVERTER.parameterizedBy(elementClassName, payloadTypeName.copy(nullable = false))
        )
        .addFunction(
            FunSpec.builder("convert")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("source", elementClassName)
                .returns(payloadTypeName.copy(nullable = false))
                .addStatement("return ${element.kotlin.read("source")}")
                .build()
        )
        .build()
}

@Suppress("LongParameterList")
private fun buildSpringDataConfiguration(
    converterSpecs: Map<SpringDataStore.ConverterKind, List<TypeSpec>>,
    userConverters: Map<SpringDataStore, List<String>>,
    context: Generator.Context,
    redis: Boolean,
    elements: NonEmptySet<ValidatedKspGeneratorElement>,
    configuration: ClassName,
): TypeSpec {
    val hasConditionalOnMissingBean = context.isOnClasspath(
        "org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean"
    )

    val configBuilder = TypeSpec.classBuilder(configuration)
        .addGeneratedAnnotation(SpringDataGenerator::class, context)
        .addAnnotation(CONFIGURATION)
        .addKdoc("""
            Generated Spring Data converter configuration.

            Registers all read/write converters for types annotated with `@Lazyval`
            with the appropriate Spring Data store-specific conversion service.

            Generated by the Lazyval annotation processor. Do not modify.
        """.trimIndent())

    userConverters.forEach { (store, userFqns) ->
        configBuilder.addFunction(
            buildBeanMethod(store, converterSpecs.getValue(store.converterKind), userFqns, hasConditionalOnMissingBean)
        )
    }

    if (redis) {
        configBuilder.addType(buildRedisCompanion())
        configBuilder.addType(buildRedisSerializers(elements))
        configBuilder.addType(buildRedisCacheKeys(elements, redisSerializersOf(configuration)))
    }

    return configBuilder.build()
}

private fun buildBeanMethod(
    store: SpringDataStore,
    generated: List<TypeSpec>,
    userFqns: List<String>,
    hasConditionalOnMissingBean: Boolean
): FunSpec {
    val builder = FunSpec.builder(store.beanName)
        .addAnnotation(BEAN)
        .returns(store.conversionsType)
    if (hasConditionalOnMissingBean) {
        builder.addAnnotation(CONDITIONAL_ON_MISSING_BEAN)
    }
    store.construction.parameters().forEach { builder.addParameter(it) }
    builder.addStatement(
        "val converters = listOf(\n%L\n)",
        buildConverterListInit(generated, userFqns, store.optionKey)
    )
    store.construction.addReturnStatement(builder, store.conversionsType)
    return builder.build()
}

private fun buildConverterListInit(
    generated: List<TypeSpec>,
    userFqns: List<String>,
    userOptionKey: String
): String = buildString {
    generated.forEachIndexed { i, spec ->
        append("    ").append(spec.name).append("()")
        val trailingComma = i < generated.size - 1 || userFqns.isNotEmpty()
        if (trailingComma) append(",")
        append("\n")
    }
    if (userFqns.isNotEmpty()) {
        append("    // user-supplied via ").append(userOptionKey).append(":\n")
        userFqns.forEachIndexed { i, fqn ->
            append("    ").append(fqn).append("()")
            if (i < userFqns.size - 1) append(",")
            append("\n")
        }
    }
}.trimEnd('\n', ',')
