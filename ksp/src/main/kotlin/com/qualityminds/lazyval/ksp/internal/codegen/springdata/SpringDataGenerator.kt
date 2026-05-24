package com.qualityminds.lazyval.ksp.internal.codegen.springdata

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
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
 * The wrapped type inside a domain-primitive is always non-nullable (lazyval rejects nullable
 * wrapped properties at compile time). Write converters therefore always return a non-null
 * value: `Converter<DomainType, InnerType>`.
 */
class SpringDataGenerator : Generator {

    companion object {
        private const val GENERATOR_ID = "spring-data"
        private const val OPTION_GENERATED_PACKAGE = "lazyval.springdata.package"
        private const val OPTION_CASSANDRA_CONVERTERS = "lazyval.springdata.cassandra.converters"
        private const val OPTION_MONGO_CONVERTERS = "lazyval.springdata.mongo.converters"

        private const val CASSANDRA_CUSTOM_CONVERSIONS_FQN = "org.springframework.data.cassandra.core.convert.CassandraCustomConversions"
        private const val MONGO_CUSTOM_CONVERSIONS_FQN = "org.springframework.data.mongodb.core.convert.MongoCustomConversions"
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
        private val CASSANDRA_CUSTOM_CONVERSIONS = ClassName(
            "org.springframework.data.cassandra.core.convert", "CassandraCustomConversions"
        )
        private val MONGO_CUSTOM_CONVERSIONS = ClassName(
            "org.springframework.data.mongodb.core.convert", "MongoCustomConversions"
        )
    }

    override fun generatorId(): String = GENERATOR_ID

    override fun requiredClasspath(): Collection<String> =
        listOf("org.springframework.data.convert.ReadingConverter")

    override fun supportedOptions(): Set<String> =
        setOf(OPTION_GENERATED_PACKAGE, OPTION_CASSANDRA_CONVERTERS, OPTION_MONGO_CONVERTERS)

    override fun generate(
        validatedElements: NonEmptySet<ValidatedKspGeneratorElement>,
        context: Generator.Context
    ): Stream<GeneratorResult> {
        val isCassandra = context.isOnClasspath(CASSANDRA_CUSTOM_CONVERSIONS_FQN)
        val isMongo = context.isOnClasspath(MONGO_CUSTOM_CONVERSIONS_FQN)

        if (!isCassandra && !isMongo) {
            return Stream.empty()
        }

        val converterPackage = context.generatorPackage(OPTION_GENERATED_PACKAGE, "boundary.persistence")

        val cassandraUserFqns = if (isCassandra) {
            validateUserConverters(OPTION_CASSANDRA_CONVERTERS, context, converterPackage)
        } else {
            warnIfOptionSetForMissingStorage(OPTION_CASSANDRA_CONVERTERS, "Cassandra", context)
            emptyList()
        }
        val mongoUserFqns = if (isMongo) {
            validateUserConverters(OPTION_MONGO_CONVERTERS, context, converterPackage)
        } else {
            warnIfOptionSetForMissingStorage(OPTION_MONGO_CONVERTERS, "MongoDB", context)
            emptyList()
        }

        val converterSpecs = mutableListOf<TypeSpec>()
        for (element in validatedElements) {
            converterSpecs += buildReadConverter(element)
            converterSpecs += buildWriteConverter(element)
        }

        val configSpec = buildSpringDataConfiguration(
            converterSpecs, isCassandra, isMongo, cassandraUserFqns, mongoUserFqns, context
        )

        val file = FileSpec.builder(converterPackage, "LazyvalSpringDataConfiguration")
            .addType(configSpec)
            .apply { converterSpecs.forEach { addType(it) } }
            .build()

        return Stream.of(GeneratorResult.Kotlin(
            GeneratorResult.Metadata(file.packageName, file.name),
            file.toString()
        ))
    }

    private fun validateUserConverters(optionKey: String, context: Generator.Context, converterPackage: String): List<String> {
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

    private fun warnIfOptionSetForMissingStorage(optionKey: String, storageLabel: String, context: Generator.Context) {
        val raw = context.getSetting(optionKey)
        if (!raw.isNullOrBlank()) {
            context.logWarning(this, "$optionKey is set but $storageLabel Spring Data is not on the classpath; the option will be ignored")
        }
    }

    private fun buildReadConverter(element: ValidatedKspGeneratorElement): TypeSpec {
        val elementClassName = element.element.toClassName()
        val wrappedTypeName = element.wrappedProperty.type.toTypeName()
        val factoryReturnsNullable = element.factoryMethod?.returnType?.resolve()?.isMarkedNullable ?: false
        val returnType = elementClassName.copy(nullable = factoryReturnsNullable)

        return TypeSpec.classBuilder("${element.typeName}ReadConverter")
            .addModifiers(KModifier.PRIVATE)
            .addAnnotation(READING_CONVERTER)
            .addSuperinterface(
                CONVERTER.parameterizedBy(wrappedTypeName.copy(nullable = false), returnType)
            )
            .addFunction(
                FunSpec.builder("convert")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("source", wrappedTypeName.copy(nullable = false))
                    .returns(returnType)
                    .addStatement("return ${element.objectCreation("source")}")
                    .build()
            )
            .build()
    }

    private fun buildWriteConverter(element: ValidatedKspGeneratorElement): TypeSpec {
        val elementClassName = element.element.toClassName()
        val wrappedTypeName = element.wrappedProperty.type.toTypeName()

        return TypeSpec.classBuilder("${element.typeName}WriteConverter")
            .addModifiers(KModifier.PRIVATE)
            .addAnnotation(WRITING_CONVERTER)
            .addSuperinterface(
                CONVERTER.parameterizedBy(elementClassName, wrappedTypeName.copy(nullable = false))
            )
            .addFunction(
                FunSpec.builder("convert")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("source", elementClassName)
                    .returns(wrappedTypeName.copy(nullable = false))
                    .addStatement("return source.${element.kotlinAccessor}")
                    .build()
            )
            .build()
    }

    private fun buildSpringDataConfiguration(
        converterSpecs: List<TypeSpec>,
        isCassandra: Boolean, isMongo: Boolean,
        cassandraUserFqns: List<String>, mongoUserFqns: List<String>,
        context: Generator.Context
    ): TypeSpec {
        val hasConditionalOnMissingBean = context.isOnClasspath(
            "org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean"
        )

        val configBuilder = TypeSpec.classBuilder("LazyvalSpringDataConfiguration")
            .addAnnotation(CONFIGURATION)
            .addKdoc("""
                Generated Spring Data converter configuration.

                Registers all read/write converters for types annotated with `@Lazyval`
                with the appropriate Spring Data store-specific conversion service.

                Generated by the Lazyval annotation processor. Do not modify.
            """.trimIndent())

        if (isCassandra) {
            configBuilder.addFunction(buildBeanMethod(
                "cassandraCustomConversions", CASSANDRA_CUSTOM_CONVERSIONS,
                converterSpecs, cassandraUserFqns, OPTION_CASSANDRA_CONVERTERS, hasConditionalOnMissingBean
            ))
        }
        if (isMongo) {
            configBuilder.addFunction(buildBeanMethod(
                "mongoCustomConversions", MONGO_CUSTOM_CONVERSIONS,
                converterSpecs, mongoUserFqns, OPTION_MONGO_CONVERTERS, hasConditionalOnMissingBean
            ))
        }

        return configBuilder.build()
    }

    private fun buildBeanMethod(
        methodName: String,
        conversionsType: ClassName,
        generated: List<TypeSpec>,
        userFqns: List<String>,
        userOptionKey: String,
        hasConditionalOnMissingBean: Boolean
    ): FunSpec {
        val listInit = buildString {
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

        return FunSpec.builder(methodName)
            .addAnnotation(BEAN)
            .returns(conversionsType)
            .apply {
                if (hasConditionalOnMissingBean) {
                    addAnnotation(CONDITIONAL_ON_MISSING_BEAN)
                }
            }
            .addStatement("val converters = listOf(\n%L\n)", listInit)
            .addStatement("return %T(converters)", conversionsType)
            .build()
    }
}
