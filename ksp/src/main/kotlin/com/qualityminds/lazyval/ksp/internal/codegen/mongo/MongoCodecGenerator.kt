package com.qualityminds.lazyval.ksp.internal.codegen.mongo

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.internal.codegen.GeneratedStamp.addGeneratedAnnotation
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
 * Generates a native MongoDB driver `Codec` for each domain-primitive, grouped into a
 * `LazyvalMongoCodecs` class that implements `CodecProvider`.
 *
 * The provider resolves each primitive's inner-type codec from the supplied `CodecRegistry`
 * on demand and delegates `encode`/`decode` to it, which transparently picks up whatever
 * representation the registry has configured for the wrapped type (e.g. UUID representation,
 * date/time codecs).
 *
 * ## Null handling — Mongo driver convention
 *
 * The generated codecs follow the MongoDB Java driver convention: property-level codecs
 * operate on non-null values and assume the `BsonReader` is positioned on a non-null BSON
 * token. This matches how the driver's own stock codecs (`StringCodec`, `IntegerCodec`,
 * `DateCodec`, ...) behave — none of them null-guard their `encode`/`decode`.
 *
 * The standard call paths all pre-filter BSON `NULL` before invoking property codecs:
 * `PojoCodec` writes `writeNull()` directly for null fields on encode and sets the property
 * to `null` without invoking the codec on decode; `IterableCodec`, `MapCodec`, and array
 * codecs apply the same filter at the element level.
 *
 * **Garbage-in / garbage-out.** Invoking `encode` with a `null` value, or `decode` on a
 * reader positioned at a BSON `NULL` token, is a contract violation by the caller. The exact
 * behavior in that case — `NullPointerException`, `BsonInvalidOperationException` from the
 * inner reader, or a domain `IllegalArgumentException` from the wrapper's factory — is
 * intentionally undefined and depends on the inner codec and the wrapper type. Callers using
 * non-standard direct codec lookups are responsible for filtering BSON nulls themselves.
 *
 * When a wrapper's factory method is declared with a nullable return type (e.g.
 * `fun ofNullable(...): CouponCode?`), the generated codec is typed as `Codec<Wrapper?>` so
 * the nullable decode return path is visible at the Kotlin level. At JVM erasure this is
 * indistinguishable from `Codec<Wrapper>`, so registry lookups for `Codec<Wrapper>` resolve
 * correctly.
 *
 * ## Quarkus integration
 *
 * When the `quarkus-mongodb-client` extension is detected on the classpath, a
 * `LazyvalMongoCodecRegistrar` `@ApplicationScoped` `CodecProvider` bean is also generated.
 * Quarkus auto-discovers `CodecProvider` CDI beans and chains them into the default Mongo
 * registry — no further wiring is needed.
 */
@Suppress("LongMethod", "TooManyFunctions") // TODO refactor
class MongoCodecGenerator : Generator {

    companion object {
        private const val OPTION_GENERATED_PACKAGE = "lazyval.mongodb.package"
        private const val OPTION_USER_CODECS = "lazyval.mongodb.codecs"
        private const val OPTION_QUARKUS_REGISTER = "lazyval.mongodb.quarkus.register"

        private const val CODEC_FQN = "org.bson.codecs.Codec"
        private const val MONGO_CLIENT_SETTINGS_FQN = "com.mongodb.MongoClientSettings"
        private const val QUARKUS_MONGO_MARKER = "io.quarkus.mongodb.MongoClientName"

        private val CODEC = ClassName("org.bson.codecs", "Codec")
        private val BSON_READER = ClassName("org.bson", "BsonReader")
        private val BSON_WRITER = ClassName("org.bson", "BsonWriter")
        private val BSON_TYPE = ClassName("org.bson", "BsonType")
        private val ENCODER_CONTEXT = ClassName("org.bson.codecs", "EncoderContext")
        private val DECODER_CONTEXT = ClassName("org.bson.codecs", "DecoderContext")
        private val CODEC_REGISTRY = ClassName("org.bson.codecs.configuration", "CodecRegistry")
        private val CODEC_REGISTRIES = ClassName("org.bson.codecs.configuration", "CodecRegistries")
        private val CODEC_PROVIDER = ClassName("org.bson.codecs.configuration", "CodecProvider")
        private val MONGO_CLIENT_SETTINGS = ClassName("com.mongodb", "MongoClientSettings")
        private val SYSTEM_LOGGER = ClassName("java.lang", "System", "Logger")
        private val SYSTEM_LOGGER_LEVEL = ClassName("java.lang", "System", "Logger", "Level")
        private val JAVA_LANG_SYSTEM = ClassName("java.lang", "System")

        private const val CODECS_CLASS_NAME = "LazyvalMongoCodecs"
        private const val REGISTRAR_CLASS_NAME = "LazyvalMongoCodecRegistrar"
    }

    override fun generatorId(): String = StockGeneratorIds.MONGODB_CODEC

    override fun requiredClasspath(): Set<String> = setOf(CODEC_FQN)

    override fun supportedOptions(): Set<String> =
        setOf(OPTION_GENERATED_PACKAGE, OPTION_USER_CODECS, OPTION_QUARKUS_REGISTER)

    override fun generate(
        validatedElements: NonEmptySet<ValidatedKspGeneratorElement>,
        context: Generator.Context
    ): Stream<GeneratorResult> {
        val codecPackage = context.generatorPackage(OPTION_GENERATED_PACKAGE, "boundary.persistence.mongodb")

        val userCodecFqns = validateUserCodecs(context, codecPackage)

        val orderedElements = validatedElements.toList()
        val codecSpecs = orderedElements.map { buildCodec(it) }

        val results = mutableListOf<GeneratorResult>()
        if (codecSpecs.isEmpty()) {
            return results.stream()
        }

        val hasMongoDriverCore = context.isOnClasspath(MONGO_CLIENT_SETTINGS_FQN)
        val utilitySpec = buildCodecsUtility(context, orderedElements, codecSpecs, userCodecFqns, hasMongoDriverCore)
        val utilityFile = FileSpec.builder(codecPackage, utilitySpec.name!!)
            .addType(utilitySpec)
            .build()
        results += GeneratorResult.Kotlin(
            GeneratorResult.Metadata(utilityFile.packageName, utilityFile.name),
            utilityFile.toString()
        )

        val isQuarkus = context.isOnClasspath(QUARKUS_MONGO_MARKER)
        val quarkusRegister = context.getSetting(OPTION_QUARKUS_REGISTER)
            ?.let { !"false".equals(it, ignoreCase = true) }
            ?: true

        if (isQuarkus && quarkusRegister) {
            val registrarSpec = buildQuarkusRegistrar(context, codecPackage)
            val registrarFile = FileSpec.builder(codecPackage, registrarSpec.name!!)
                .addType(registrarSpec)
                .build()
            results += GeneratorResult.Kotlin(
                GeneratorResult.Metadata(registrarFile.packageName, registrarFile.name),
                registrarFile.toString()
            )
        }
        return results.stream()
    }

    private fun NonEmptySet<ValidatedKspGeneratorElement>.toList(): List<ValidatedKspGeneratorElement> {
        val out = mutableListOf<ValidatedKspGeneratorElement>()
        for (e in this) out += e
        return out
    }

    private fun validateUserCodecs(context: Generator.Context, codecPackage: String): List<String> {
        val raw = context.getSetting(OPTION_USER_CODECS) ?: return emptyList()
        if (raw.isBlank()) return emptyList()

        val fqns = raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val valid = mutableListOf<String>()
        for (fqn in fqns) {
            val info = context.inspectClass(fqn)
            if (info == null) {
                context.logError(this, "$OPTION_USER_CODECS: class '$fqn' not found on compile classpath")
                continue
            }
            var ok = true
            if (!info.isAssignableTo(CODEC_FQN)) {
                context.logError(this, "$OPTION_USER_CODECS: class '$fqn' does not implement $CODEC_FQN")
                ok = false
            }
            if (!info.isAccessibleFrom(codecPackage)) {
                context.logError(this, "$OPTION_USER_CODECS: class '$fqn' is not accessible from the generated codecs at package '$codecPackage'")
                ok = false
            }
            if (!info.hasAccessibleNoArgConstructor(codecPackage)) {
                context.logError(this, "$OPTION_USER_CODECS: class '$fqn' must declare a no-arg constructor accessible from the generated codecs at package '$codecPackage'")
                ok = false
            }
            if (ok) {
                valid += fqn
            }
        }
        return valid
    }

    private fun buildCodec(element: ValidatedKspGeneratorElement): TypeSpec {
        val elementClassName = element.element.toClassName()
        val wrappedTypeName = element.wrappedProperty.type.toTypeName().copy(nullable = false)
        val factoryReturnsNullable = element.factoryMethod?.returnType?.resolve()?.isMarkedNullable ?: false
        val outerType = elementClassName.copy(nullable = factoryReturnsNullable)

        val codecClassName = "${element.typeName.name}Codec"
        val innerCodecTypeName = CODEC.parameterizedBy(wrappedTypeName)

        val constructor = FunSpec.constructorBuilder()
            .addParameter("innerCodec", innerCodecTypeName)
            .build()

        val innerCodecProperty = PropertySpec.builder("innerCodec", innerCodecTypeName, KModifier.PRIVATE)
            .initializer("innerCodec")
            .build()

        val encode = FunSpec.builder("encode")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("writer", BSON_WRITER)
            .addParameter("value", outerType)
            .addParameter("encoderContext", ENCODER_CONTEXT)
            .apply {
                if (factoryReturnsNullable) {
                    addCode(
                        """
                        |if (value == null) {
                        |    writer.writeNull()
                        |    return
                        |}
                        |innerCodec.encode(writer, value.${element.kotlinAccessor}, encoderContext)
                        |""".trimMargin()
                    )
                } else {
                    addStatement("innerCodec.encode(writer, value.${element.kotlinAccessor}, encoderContext)")
                }
            }
            .build()

        val decode = FunSpec.builder("decode")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("reader", BSON_READER)
            .addParameter("decoderContext", DECODER_CONTEXT)
            .returns(outerType)
            .addStatement("return ${element.objectCreation("innerCodec.decode(reader, decoderContext)")}")
            .build()

        val getEncoderClass = FunSpec.builder("getEncoderClass")
            .addModifiers(KModifier.OVERRIDE)
            .returns(ClassName("java.lang", "Class").parameterizedBy(outerType))
            .apply {
                if (factoryReturnsNullable) {
                    addAnnotation(
                        AnnotationSpec.builder(Suppress::class).addMember("%S", "UNCHECKED_CAST").build()
                    )
                    addStatement(
                        "return %T::class.java as Class<%T>",
                        elementClassName, outerType
                    )
                } else {
                    addStatement("return %T::class.java", elementClassName)
                }
            }
            .build()

        return TypeSpec.classBuilder(codecClassName)
            .addKdoc(
                "BSON codec for [%T]. Follows the MongoDB driver convention: invoked only on " +
                    "non-null BSON tokens. Standard call paths (PojoCodec, IterableCodec, ...) " +
                    "filter nulls at the document/element level before invoking property codecs. " +
                    "Direct invocation with a null value or a reader at a BSON NULL token is a " +
                    "contract violation and the behavior is undefined.",
                elementClassName
            )
            .addSuperinterface(CODEC.parameterizedBy(outerType))
            .primaryConstructor(constructor)
            .addProperty(innerCodecProperty)
            .addFunction(encode)
            .addFunction(decode)
            .addFunction(getEncoderClass)
            .build()
    }

    private fun buildCodecsUtility(
        context: Generator.Context,
        elements: List<ValidatedKspGeneratorElement>,
        codecSpecs: List<TypeSpec>,
        userCodecFqns: List<String>,
        hasMongoDriverCore: Boolean
    ): TypeSpec {
        val codecArrayType = ARRAY.parameterizedBy(CODEC.parameterizedBy(STAR))

        val userCodecsProperty = PropertySpec.builder("userCodecs", codecArrayType, KModifier.PRIVATE)
            .initializer(buildUserCodecsInitializer(userCodecFqns))
            .build()

        val constructor = if (userCodecFqns.isEmpty()) {
            FunSpec.constructorBuilder().build()
        } else {
            buildOverrideDetectionInit(elements)
        }

        val getFun = buildGetFun(elements, userCodecFqns.isNotEmpty())

        val builder = TypeSpec.classBuilder(CODECS_CLASS_NAME)
            .addGeneratedAnnotation(MongoCodecGenerator::class, context)
            .addSuperinterface(CODEC_PROVIDER)
            .primaryConstructor(constructor)
            .addProperty(userCodecsProperty)
            .addFunction(getFun)

        if (hasMongoDriverCore) {
            val asRegistry = FunSpec.builder("asRegistry")
                .addKdoc(
                    """
                    Convenience function returning a [%T] that combines the default Mongo
                    registry with this provider. Use it for one-line setup outside of CDI:
                    ```
                    val settings = MongoClientSettings.builder()
                        .codecRegistry(LazyvalMongoCodecs.asRegistry())
                        .build()
                    ```

                    @return a `CodecRegistry` with the default registry and the generated codecs
                    """.trimIndent(),
                    CODEC_REGISTRY
                )
                .addModifiers(KModifier.PUBLIC)
                .returns(CODEC_REGISTRY)
                .addStatement(
                    "return %T.fromRegistries(%T.getDefaultCodecRegistry(), %T.fromProviders(%L()))",
                    CODEC_REGISTRIES, MONGO_CLIENT_SETTINGS, CODEC_REGISTRIES, CODECS_CLASS_NAME
                )
                .build()
            val companion = TypeSpec.companionObjectBuilder()
                .addFunction(asRegistry)
                .build()
            builder.addType(companion)
        }

        codecSpecs.forEach { spec ->
            builder.addType(spec.toBuilder().addModifiers(KModifier.INTERNAL).build())
        }

        return builder.build()
    }

    private fun buildUserCodecsInitializer(userCodecFqns: List<String>): CodeBlock {
        if (userCodecFqns.isEmpty()) {
            return CodeBlock.of("arrayOf()")
        }
        val body = userCodecFqns.joinToString(",\n") { "    $it()" }
        return CodeBlock.of("arrayOf(\n%L\n)", body)
    }

    private fun buildOverrideDetectionInit(elements: List<ValidatedKspGeneratorElement>): FunSpec {
        val generatedTypesList = elements.joinToString(",\n") { "    ${it.element.toClassName().simpleName}::class.java" }
        return FunSpec.constructorBuilder()
            .addCode(
                """
                |val logger = %T.getLogger(%L::class.java.name)
                |val generatedTypes = setOf<Class<*>>(
                |%L
                |)
                |for (userCodec in userCodecs) {
                |    if (userCodec.encoderClass in generatedTypes) {
                |        logger.log(%T.INFO) {
                |            "User-supplied codec ${'$'}{userCodec::class.java.name} overrides the generated codec for ${'$'}{userCodec.encoderClass.name}"
                |        }
                |    }
                |}
                |""".trimMargin(),
                JAVA_LANG_SYSTEM, CODECS_CLASS_NAME, generatedTypesList, SYSTEM_LOGGER_LEVEL
            )
            .build()
    }

    private fun buildGetFun(elements: List<ValidatedKspGeneratorElement>, hasUserCodecs: Boolean): FunSpec {
        val typeT = TypeVariableName("T", ANY.copy(nullable = true))
        val classOfT = ClassName("java.lang", "Class").parameterizedBy(typeT)
        val codecOfT = CODEC.parameterizedBy(typeT).copy(nullable = true)

        val builder = FunSpec.builder("get")
            .addModifiers(KModifier.OVERRIDE)
            .addAnnotation(
                AnnotationSpec.builder(Suppress::class).addMember("%S", "UNCHECKED_CAST").build()
            )
            .addTypeVariable(typeT)
            .returns(codecOfT)
            .addParameter("clazz", classOfT)
            .addParameter("registry", CODEC_REGISTRY)

        if (hasUserCodecs) {
            builder.addCode(
                """
                |// user codecs override generated ones (last-wins)
                |for (userCodec in userCodecs) {
                |    if (userCodec.encoderClass == clazz) {
                |        return userCodec as %T
                |    }
                |}
                |""".trimMargin(),
                codecOfT
            )
        }

        for (element in elements) {
            val elementClassName = element.element.toClassName()
            val wrappedTypeName = element.wrappedProperty.type.toTypeName().copy(nullable = false)
            val codecClassName = "${element.typeName.name}Codec"

            builder.addCode(
                """
                |if (clazz == %T::class.java) {
                |    return %L(registry.get(%T::class.javaObjectType)) as %T
                |}
                |""".trimMargin(),
                elementClassName, codecClassName, wrappedTypeName, codecOfT
            )
        }

        builder.addStatement("return null")
        return builder.build()
    }

    private fun buildQuarkusRegistrar(context: Generator.Context, codecPackage: String): TypeSpec {
        val applicationScoped = ClassName("jakarta.enterprise.context", "ApplicationScoped")
        val unremovable = ClassName("io.quarkus.arc", "Unremovable")
        val codecsClass = ClassName(codecPackage, CODECS_CLASS_NAME)
        val typeT = TypeVariableName("T", ANY.copy(nullable = true))
        val classOfT = ClassName("java.lang", "Class").parameterizedBy(typeT)
        val codecOfT = CODEC.parameterizedBy(typeT).copy(nullable = true)

        val delegateProperty = PropertySpec.builder("delegate", codecsClass, KModifier.PRIVATE)
            .initializer("%T()", codecsClass)
            .build()

        val getFun = FunSpec.builder("get")
            .addModifiers(KModifier.OVERRIDE)
            .addTypeVariable(typeT)
            .returns(codecOfT)
            .addParameter("clazz", classOfT)
            .addParameter("registry", CODEC_REGISTRY)
            .addStatement("return delegate.get(clazz, registry)")
            .build()

        return TypeSpec.classBuilder(REGISTRAR_CLASS_NAME)
            .addGeneratedAnnotation(MongoCodecGenerator::class, context)
            .addAnnotation(applicationScoped)
            .addAnnotation(unremovable)
            .addSuperinterface(CODEC_PROVIDER)
            .addProperty(delegateProperty)
            .addFunction(getFun)
            .build()
    }
}
