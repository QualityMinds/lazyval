package com.qualityminds.lazyval.ksp.internal.codegen.cassandra

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.internal.codegen.GeneratedStamp.addGeneratedAnnotation
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import java.util.stream.Stream

/**
 * Generates a DataStax `MappingCodec` for each domain-primitive, grouped into a
 * `LazyvalCassandraCodecs` utility object.
 *
 * ## Null invariants
 *
 * The DataStax driver decodes a CQL `NULL` column to `null` and passes it directly to
 * `innerToOuter`. Both methods are therefore always nullable in parameter and return type:
 * - `innerToOuter(value: InnerType?): DomainType?` — a null column returns `null`; a non-null
 *   column whose factory returns `null` (e.g. a nullable factory with a blank-string guard)
 *   also returns `null`. Both cases are handled uniformly by `value?.let { factory(it) }`.
 * - `outerToInner(value: DomainType?): InnerType?` — always nullable for symmetry and to
 *   accommodate a nullable property at the call site.
 *
 * The `MappingCodec<InnerType, DomainType?>` type parameter is always `DomainType?` (nullable)
 * to match the nullable return of `innerToOuter`. The constructor uses
 * `object : GenericType<DomainType?>() {}` instead of `GenericType.of(DomainType::class.java)`
 * to satisfy the Kotlin type constraint; at the JVM level both carry the same erased
 * `DomainType` class and are indistinguishable by the DataStax codec registry.
 */
class CassandraCodecGenerator : Generator {

    override fun generatorId(): String = GENERATOR_ID

    override fun requiredClasspath(): Collection<String> =
        listOf("com.datastax.oss.driver.api.core.type.codec.MappingCodec")

    override fun supportedOptions(): Set<String> = setOf(OPTION_GENERATED_PACKAGE, OPTION_QUARKUS_REGISTER)

    override fun generate(
        validatedElements: NonEmptySet<ValidatedKspGeneratorElement>,
        context: Generator.Context
    ): Stream<GeneratorResult> {
        val codecPackage = context.generatorPackage(OPTION_GENERATED_PACKAGE, "boundary.persistence.cassandra")

        val results = mutableListOf<GeneratorResult>()
        val codecSpecs = mutableListOf<TypeSpec>()

        for (element in validatedElements) {
            val typeCodecConstant = resolveTypeCodecConstant(element) ?: continue
            codecSpecs += buildMappingCodec(element, typeCodecConstant)
        }

        if (codecSpecs.isNotEmpty()) {
            val utilitySpec = buildCodecsUtility(context, codecSpecs)
            val fileSpec = FileSpec.builder(codecPackage, utilitySpec.name!!)
                .addType(utilitySpec)
                .build()
            results += GeneratorResult.Kotlin(
                GeneratorResult.Metadata(fileSpec.packageName, fileSpec.name),
                fileSpec.toString()
            )

            val isQuarkus = context.isOnClasspath("com.datastax.oss.quarkus.runtime.api.session.QuarkusCqlSession")
            val quarkusRegister = context.getSetting(OPTION_QUARKUS_REGISTER)
                ?.let { !"false".equals(it, ignoreCase = true) }
                ?: true

            if (isQuarkus && quarkusRegister) {
                val codecClassNames = codecSpecs.map { it.name!! }
                val registrarSpec = CassandraQuarkusRegistrar.build(context, codecClassNames)
                val registrarFile = FileSpec.builder(codecPackage, registrarSpec.name!!)
                    .addType(registrarSpec)
                    .build()
                results += GeneratorResult.Kotlin(
                    GeneratorResult.Metadata(registrarFile.packageName, registrarFile.name),
                    registrarFile.toString()
                )
            }
        }

        return results.stream()
    }

    private fun resolveTypeCodecConstant(element: ValidatedKspGeneratorElement): String? {
        val qualifiedName = element.wrappedProperty.type.declaration.qualifiedName?.asString() ?: return null
        return TYPE_CODEC_MAP[qualifiedName]
    }

    private fun buildMappingCodec(element: ValidatedKspGeneratorElement, typeCodecConstant: String): TypeSpec {
        val elementClassName = element.element.toClassName()
        val wrappedTypeName = element.wrappedProperty.type.toTypeName()

        val codecClassName = "${element.typeName.name}Codec"

        val nullableElementClassName = elementClassName.copy(nullable = true)

        return TypeSpec.classBuilder(codecClassName)
            .superclass(
                MAPPING_CODEC.parameterizedBy(wrappedTypeName.copy(nullable = false), nullableElementClassName)
            )
            .addSuperclassConstructorParameter(
                CodeBlock.of("%T.%L, object : %T() {}", TYPE_CODECS, typeCodecConstant, GENERIC_TYPE.parameterizedBy(nullableElementClassName))
            )
            .addFunction(
                FunSpec.builder("innerToOuter")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("value", wrappedTypeName.copy(nullable = true))
                    .returns(nullableElementClassName)
                    .addStatement("return value?.let { ${element.objectCreation("it")} }")
                    .build()
            )
            .addFunction(
                FunSpec.builder("outerToInner")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("value", nullableElementClassName)
                    .returns(wrappedTypeName.copy(nullable = true))
                    .addStatement("return value?.${element.kotlinAccessor}")
                    .build()
            )
            .build()
    }

    private fun buildCodecsUtility(context: Generator.Context, codecSpecs: List<TypeSpec>): TypeSpec {
        val codecClassNames = codecSpecs.map { it.name!! }

        val allFun = FunSpec.builder("all")
            .addKdoc(
                "Returns an array of all generated [%T]s for lazyval wrapper types.\n\n" +
                "Use this method to register all codecs at once, e.g.:\n" +
                "```\n" +
                "val session = CqlSession.builder()\n" +
                "    .addTypeCodecs(*LazyvalCassandraCodecs.all())\n" +
                "    .build()\n" +
                "```\n\n" +
                "@return an array containing one codec instance per generated wrapper type",
                TYPE_CODEC
            )
            .returns(ARRAY.parameterizedBy(TYPE_CODEC.parameterizedBy(STAR)))
            .addStatement(
                "return arrayOf(\n%L\n)",
                codecClassNames.joinToString(",\n") { "    ${it}()" }
            )
            .build()

        val builder = TypeSpec.objectBuilder("LazyvalCassandraCodecs")
            .addGeneratedAnnotation(CassandraCodecGenerator::class, context)
            .addFunction(allFun)

        codecSpecs.forEach { spec ->
            builder.addType(spec.toBuilder().addModifiers(KModifier.INTERNAL).build())
        }

        return builder.build()
    }

    companion object {
        private const val GENERATOR_ID = "cassandra"
        private const val OPTION_GENERATED_PACKAGE = "lazyval.cassandra.package"
        private const val OPTION_QUARKUS_REGISTER = "lazyval.cassandra.quarkus.register"

        private val MAPPING_CODEC = ClassName("com.datastax.oss.driver.api.core.type.codec", "MappingCodec")
        private val TYPE_CODECS = ClassName("com.datastax.oss.driver.api.core.type.codec", "TypeCodecs")
        private val GENERIC_TYPE = ClassName("com.datastax.oss.driver.api.core.type.reflect", "GenericType")
        private val TYPE_CODEC = ClassName("com.datastax.oss.driver.api.core.type.codec", "TypeCodec")

        /**
         * Maps Kotlin/Java type qualified names to their corresponding
         * `com.datastax.oss.driver.api.core.type.codec.TypeCodecs` constant names.
         *
         * This mapping is necessary because the generated
         * `com.datastax.oss.driver.api.core.type.codec.MappingCodec` subclasses require a
         * compile-time reference to a specific `TypeCodecs` constant (e.g., `TypeCodecs.TEXT`)
         * in their constructor call. Since the DataStax driver uses CQL type names for its constants
         * rather than Java type names, and there is no `TypeCodecs.forJavaType(Class)` lookup method,
         * we need an explicit mapping. Many CQL type names differ from their Java/Kotlin counterparts
         * (e.g., `Long` → `BIGINT`, `Instant` → `TIMESTAMP`, `BigInteger` → `VARINT`).
         */
        private val TYPE_CODEC_MAP = mapOf(
            "kotlin.String" to "TEXT",
            "java.lang.String" to "TEXT",
            "kotlin.Int" to "INT",
            "java.lang.Integer" to "INT",
            "kotlin.Long" to "BIGINT",
            "java.lang.Long" to "BIGINT",
            "kotlin.Double" to "DOUBLE",
            "java.lang.Double" to "DOUBLE",
            "kotlin.Float" to "FLOAT",
            "java.lang.Float" to "FLOAT",
            "kotlin.Boolean" to "BOOLEAN",
            "java.lang.Boolean" to "BOOLEAN",
            "kotlin.Short" to "SMALLINT",
            "java.lang.Short" to "SMALLINT",
            "kotlin.Byte" to "TINYINT",
            "java.lang.Byte" to "TINYINT",
            "java.time.LocalDate" to "DATE",
            "java.time.LocalTime" to "TIME",
            "java.time.Instant" to "TIMESTAMP",
            "java.util.UUID" to "UUID",
            "java.math.BigDecimal" to "DECIMAL",
            "java.math.BigInteger" to "VARINT",
            "java.net.InetAddress" to "INET",
            "java.nio.ByteBuffer" to "BLOB"
        )
    }
}
