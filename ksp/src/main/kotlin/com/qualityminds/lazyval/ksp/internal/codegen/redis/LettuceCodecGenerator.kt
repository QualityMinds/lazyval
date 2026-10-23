package com.qualityminds.lazyval.ksp.internal.codegen.redis

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.internal.codegen.GeneratedStamp.addGeneratedAnnotation
import com.qualityminds.lazyval.ksp.internal.codegen.constantName
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

private const val OPTION_GENERATED_PACKAGE = "lazyval.redis.lettuce.package"
private const val HOLDER = "LazyvalLettuceCodecs"

/**
 * Stands in for the payload while the factory call is built, then gets replaced by the parse expression.
 * `#` cannot occur in a Kotlin identifier, so it never collides with a factory name.
 */
private const val PAYLOAD_PLACEHOLDER = "#payload#"

private val REDIS_CODEC = ClassName("io.lettuce.core.codec", "RedisCodec")
private val STRING_CODEC = ClassName("io.lettuce.core.codec", "StringCodec")
private val BYTE_BUFFER = ClassName("java.nio", "ByteBuffer")
private const val TEXT_CODEC = "TextCodec"

/**
 * How a payload is written as text and read back — mirrors Spring Data Redis' `BinaryConverters` and
 * `Jsr310Converters`, so the bytes match. `%L` is the payload (format) or the text (parse), `%T` the
 * payload type.
 */
private class Text(val format: String, val parse: String)

private val TO_STRING_PARSE = Text("%L.toString()", "%T.parse(%L)")

private val TEXT_FORMATS: Map<String, Text> = mapOf(
    "kotlin.String" to Text("%L", "%L"),
    "kotlin.Int" to Text("%L.toString()", "%L.toInt()"),
    "kotlin.Long" to Text("%L.toString()", "%L.toLong()"),
    "kotlin.Short" to Text("%L.toString()", "%L.toShort()"),
    "kotlin.Byte" to Text("%L.toString()", "%L.toByte()"),
    "kotlin.Double" to Text("%L.toString()", "%L.toDouble()"),
    "kotlin.Float" to Text("%L.toString()", "%L.toFloat()"),
    "java.math.BigDecimal" to Text("%L.toString()", "%T(%L)"),
    "java.math.BigInteger" to Text("%L.toString()", "%T(%L)"),
    // Spring Data Redis writes booleans as 1/0 and reads 1 or true
    "kotlin.Boolean" to Text("if (%L) \"1\" else \"0\"", "%L == \"1\" || %L.equals(\"true\", ignoreCase = true)"),
    "java.util.UUID" to Text("%L.toString()", "%T.fromString(%L)"),
    // epoch millis, as Spring Data Redis writes them
    "java.util.Date" to Text("%L.time.toString()", "%T(%L.toLong())"),
    "java.time.LocalDate" to TO_STRING_PARSE,
    "java.time.LocalDateTime" to TO_STRING_PARSE,
    "java.time.LocalTime" to TO_STRING_PARSE,
    "java.time.Instant" to TO_STRING_PARSE,
    "java.time.ZonedDateTime" to TO_STRING_PARSE,
    "java.time.OffsetDateTime" to TO_STRING_PARSE,
    "java.time.OffsetTime" to TO_STRING_PARSE,
    "java.time.Duration" to TO_STRING_PARSE,
    "java.time.Period" to TO_STRING_PARSE,
    "java.time.ZoneId" to Text("%L.toString()", "%T.of(%L)"),
)

private val ENUM = Text("%L.name", "%T.valueOf(%L)")

/**
 * Generates `LazyvalLettuceCodecs`: one Lettuce `RedisCodec<T, T>` per domain-primitive, to be composed per
 * connection with `RedisCodec.of(keyCodec, valueCodec)`.
 *
 * Lettuce has no fallback for unknown types, so the codecs define the bytes themselves: the payload as UTF-8
 * text, written the way Spring Data Redis' store converters write it. Data can then be shared with an
 * application reading it through the Spring Data generator's `RedisSerializers`. Payload types without such
 * a text form are skipped with a warning — there is no central registry the generator owns, so an
 * application simply writes its own codec for them.
 */
class LettuceCodecGenerator : Generator {

    override fun generatorId(): String = StockGeneratorIds.REDIS_LETTUCE_CODEC

    override fun requiredClasspath(): Set<String> = setOf("io.lettuce.core.codec.RedisCodec")

    override fun supportedOptions(): Set<String> = setOf(OPTION_GENERATED_PACKAGE)

    override fun generate(
        validatedElements: NonEmptySet<ValidatedKspGeneratorElement>,
        context: Generator.Context
    ): Stream<GeneratorResult> {
        val codecPackage = context.generatorPackage(OPTION_GENERATED_PACKAGE, "boundary.persistence.redis")
        val codecs = mutableListOf<PropertySpec>()
        val supported = mutableListOf<ValidatedKspGeneratorElement>()
        for (element in validatedElements) {
            val text = textFor(element)
            if (text == null) {
                context.logWarning(
                    this, element.element,
                    "${element.name.canonicalName()}: no Lettuce codec is generated, its payload type " +
                        "${payloadName(element)} has no text encoding; write a RedisCodec for it yourself"
                )
                continue
            }
            codecs += textCodec(element, text, ClassName(codecPackage, TEXT_CODEC))
            supported += element
        }
        if (codecs.isEmpty() || !uniqueConstantNames(supported, context, this)) {
            return Stream.empty()
        }

        val holder = TypeSpec.objectBuilder(HOLDER)
            .addGeneratedAnnotation(LettuceCodecGenerator::class, context)
            .addKdoc(
                """
                Lettuce `RedisCodec`s, one per domain-primitive, usable as key and as value codec:
                ```
                RedisClient.create(uri).connect(RedisCodec.of(StringCodec.UTF8, %L.%L))
                ```
                A domain-primitive is stored as its wrapped value in UTF-8 text — the same bytes Spring Data
                Redis writes for that value.

                Generated by the Lazyval annotation processor. Do not modify.
                """.trimIndent(),
                HOLDER, constantName(supported.first())
            )
            .addProperties(codecs)
            .build()

        val file = FileSpec.builder(codecPackage, HOLDER)
            .addType(holder)
            .addType(buildTextCodec())
            .build()
        return Stream.of(GeneratorResult.Kotlin(GeneratorResult.Metadata(file.packageName, file.name), file.toString()))
    }
}

// ── emission ────────────────────────────────────────────────────────────────────────────────────

private fun payloadType(element: ValidatedKspGeneratorElement): TypeName =
    element.payloadType.toTypeName().copy(nullable = false)

private fun payloadName(element: ValidatedKspGeneratorElement): String =
    (payloadType(element) as? ClassName)?.canonicalName ?: payloadType(element).toString()

private fun textFor(element: ValidatedKspGeneratorElement): Text? {
    val declaration = element.payloadType.declaration
    if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.ENUM_CLASS) {
        return ENUM
    }
    return TEXT_FORMATS[payloadName(element)]
}

/**
 * A [Text] template with its value — and, where it names one, the payload type. Positional arguments,
 * since a template may use the value twice; KotlinPoet rejects positional arguments a template leaves
 * unused, hence the type only where it occurs.
 */
private fun render(template: String, value: CodeBlock, payloadType: TypeName): CodeBlock {
    val positional = template.replace("%L", "%1L").replace("%T", "%2T")
    return if ("%T" in template) CodeBlock.of(positional, value, payloadType) else CodeBlock.of(positional, value)
}

private fun textCodec(element: ValidatedKspGeneratorElement, text: Text, textCodec: ClassName): PropertySpec {
    val elementType = element.element.toClassName()
    val formatted = render(text.format, element.kotlin.read("it").kotlinPoet(), payloadType(element))
    val parsed = render(text.parse, CodeBlock.of("it"), payloadType(element))
    val created = element.kotlin.create(PAYLOAD_PLACEHOLDER).kotlinPoet(PAYLOAD_PLACEHOLDER, parsed)
    return constant(element, CodeBlock.of("%T<%T>({ %L }, { %L })", textCodec, elementType, formatted, created))
}

private fun constant(element: ValidatedKspGeneratorElement, initializer: CodeBlock): PropertySpec {
    val elementType = element.element.toClassName()
    return PropertySpec.builder(constantName(element), REDIS_CODEC.parameterizedBy(elementType, elementType))
        .addAnnotation(JvmField::class)
        .initializer(initializer)
        .build()
}

private val T = TypeVariableName("T", ANY)

/** Keys and values alike: the payload's text form as UTF-8, through Lettuce's own `StringCodec`. */
private fun buildTextCodec(): TypeSpec {
    val format = LambdaTypeName.get(parameters = arrayOf(T), returnType = STRING)
    val parse = LambdaTypeName.get(parameters = arrayOf(STRING), returnType = T.copy(nullable = true))
    val nullableT = T.copy(nullable = true)
    return TypeSpec.classBuilder(TEXT_CODEC)
        .addModifiers(KModifier.PRIVATE)
        .addTypeVariable(T)
        .addSuperinterface(REDIS_CODEC.parameterizedBy(T, T))
        .primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter("format", format)
                .addParameter("parse", parse)
                .build()
        )
        .addProperty(PropertySpec.builder("format", format, KModifier.PRIVATE).initializer("format").build())
        .addProperty(PropertySpec.builder("parse", parse, KModifier.PRIVATE).initializer("parse").build())
        .addFunction(
            FunSpec.builder("decodeKey").addModifiers(KModifier.OVERRIDE)
                .addParameter("bytes", BYTE_BUFFER).returns(nullableT)
                .addStatement("return parse(%T.UTF8.decodeKey(bytes))", STRING_CODEC).build()
        )
        .addFunction(
            FunSpec.builder("decodeValue").addModifiers(KModifier.OVERRIDE)
                .addParameter("bytes", BYTE_BUFFER).returns(nullableT)
                .addStatement("return parse(%T.UTF8.decodeValue(bytes))", STRING_CODEC).build()
        )
        .addFunction(
            FunSpec.builder("encodeKey").addModifiers(KModifier.OVERRIDE)
                .addParameter("key", nullableT).returns(BYTE_BUFFER)
                .addStatement("return %T.UTF8.encodeKey(key?.let(format))", STRING_CODEC).build()
        )
        .addFunction(
            FunSpec.builder("encodeValue").addModifiers(KModifier.OVERRIDE)
                .addParameter("value", nullableT).returns(BYTE_BUFFER)
                .addStatement("return %T.UTF8.encodeValue(value?.let(format))", STRING_CODEC).build()
        )
        .build()
}
