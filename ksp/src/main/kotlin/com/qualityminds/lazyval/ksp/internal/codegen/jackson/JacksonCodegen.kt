package com.qualityminds.lazyval.ksp.internal.codegen.jackson

import com.qualityminds.lazyval.ksp.internal.codegen.kotlinPoet
import com.qualityminds.lazyval.ksp.internal.codegen.GeneratedStamp.addGeneratedAnnotation
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.naming.Payload
import com.qualityminds.lazyval.naming.Payload.Kind
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

/**
 * Shared code-generation logic for Jackson serializer/deserializer modules.
 * Parameterized by [GeneratorConfig] to handle Jackson 2 vs 3 differences.
 *
 * ## Null invariants
 *
 * **Serializer:** Jackson never calls `serialize` for a `null` value; null is intercepted
 * upstream at the property-binding level. The generated `serialize(value: DomainType)` method
 * therefore always receives a non-null argument and does not need to guard against null.
 *
 * **Deserializer:** Jackson never calls `deserialize` for a JSON `null` token; null tokens
 * are resolved upstream before the deserializer is invoked. The factory method is therefore
 * always called with a non-null raw value.
 *
 * The generated `StdDeserializer` type parameter and `deserialize` return type reflect the
 * factory method's declared nullability:
 * - **Non-nullable factory** → `StdDeserializer<DomainType>`, `deserialize(): DomainType`
 * - **Nullable factory** → `StdDeserializer<DomainType?>`, `deserialize(): DomainType?`
 *
 * Jackson does **not** throw when a deserializer returns `null` for a non-null JSON value.
 * The `null` is set on the target field silently. If the target field is a non-nullable Kotlin
 * property, a `NullPointerException` will be thrown at first access, not during deserialization.
 * Any property holding a type with a nullable factory **must** be declared as `DomainType?`.
 */
internal class JacksonCodegen(private val generatorConfig: GeneratorConfig) {

    fun generateSerializer(element: ValidatedKspGeneratorElement): TypeSpec {
        val elementClassName = element.element.toClassName()
        val serializerName = "${element.name.flatName()}Serializer"

        val needsCachedSerializer = !element.isPayloadPrimitive && !isStringType(element.payloadType)

        val serializeBody: FunSpec.Builder.() -> Unit = when {
            element.isPayloadPrimitive -> {
                {
                    // Exhaustive over Kind on purpose: no else arm, so adding a primitive kind is
                    // a compile error here rather than a silent fall-through to writeString.
                    val writeStatement = when (element.primitiveKind()) {
                        Kind.INT, Kind.LONG, Kind.SHORT, Kind.FLOAT, Kind.DOUBLE ->
                            "gen.writeNumber(${element.kotlin.read("value")})"
                        Kind.BOOLEAN -> "gen.writeBoolean(${element.kotlin.read("value")})"
                        Kind.CHAR, Kind.BYTE -> "gen.writeString(${element.kotlin.read("value")}.toString())"
                    }
                    addStatement(writeStatement)
                }
            }
            isStringType(element.payloadType) -> {
                {
                    // Direct write — avoids per-call serializer lookup. Bypasses any user-customized
                    // String serializer; acceptable for scalar wrapper payloads.
                    addStatement("gen.writeString(${element.kotlin.read("value")})")
                }
            }
            else -> {
                {
                    // Resolve the delegate serializer (e.g. JavaTimeModule for DateTime types) once
                    // and cache it. Benign data race on assignment: redundant resolution yields an
                    // equivalent serializer, never an incorrect one.
                    addStatement(
                        "val ser = innerSerializer ?: ctx.findValueSerializer(%T::class.java).also { innerSerializer = it }",
                        element.payloadType.toTypeName()
                    )
                    addStatement("ser.serialize(${element.kotlin.read("value")}, gen, ctx)")
                }
            }
        }

        val builder = TypeSpec.classBuilder(serializerName)
            .superclass(generatorConfig.stdSerializer().parameterizedBy(elementClassName))
            .addSuperclassConstructorParameter(CodeBlock.of("%T::class.java", elementClassName))

        if (needsCachedSerializer) {
            val cachedFieldType = generatorConfig.valueSerializer().parameterizedBy(ANY).copy(nullable = true)
            builder.addProperty(
                PropertySpec.builder("innerSerializer", cachedFieldType)
                    .addModifiers(KModifier.PRIVATE)
                    .mutable(true)
                    .initializer("null")
                    .build()
            )
        }

        return builder
            .addFunction(
                FunSpec.builder("serialize")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("value", elementClassName)
                    .addParameter("gen", generatorConfig.jsonGenerator())
                    .addParameter("ctx", generatorConfig.serializerProvider())
                    .apply(serializeBody)
                    .build()
            )
            .build()
    }

    fun generateDeserializer(element: ValidatedKspGeneratorElement): TypeSpec {
        val elementClassName = element.element.toClassName()
        val deserializerName = "${element.name.flatName()}Deserializer"
        val returnType = elementClassName.copy(nullable = element.kotlin.createIsNullable)

        val deserializeMethod = FunSpec.builder("deserialize")
            .addModifiers(KModifier.OVERRIDE)
            .returns(returnType)
            .addParameter("p", generatorConfig.jsonParser())
            .addParameter("ctx", generatorConfig.deserializationContext())
            .apply(deserializeBody(element))

        val cachedFieldType = generatorConfig.valueDeserializer().parameterizedBy(ANY).copy(nullable = true)
        return TypeSpec.classBuilder(deserializerName)
            .superclass(generatorConfig.stdDeserializer().parameterizedBy(returnType))
            .addSuperclassConstructorParameter(CodeBlock.of("%T::class.java", elementClassName))
            .addProperty(
                PropertySpec.builder("innerDeserializer", cachedFieldType)
                    .addModifiers(KModifier.PRIVATE)
                    .mutable(true)
                    .initializer("null")
                    .build()
            )
            .addFunction(deserializeMethod.build())
            .build()
    }

    private fun deserializeBody(
        element: ValidatedKspGeneratorElement
    ): FunSpec.Builder.() -> Unit {
        val payloadType = element.payloadType.toTypeName()
        val directRead = directRead(element)
        return {
            if (directRead != null) {
                beginControlFlow("val value = if (%L)", directRead.condition)
                addStatement("%L", directRead.read)
                nextControlFlow("else")
                addDelegateLookup(payloadType)
                addStatement("deser.deserialize(p, ctx) as %T", payloadType)
                endControlFlow()
            } else {
                addDelegateLookup(payloadType)
                addStatement("val value = deser.deserialize(p, ctx) as %T", payloadType)
            }
            addStatement("return %L", element.kotlin.create("value").kotlinPoet())
        }
    }

    /**
     * Resolves Jackson's own deserializer for the payload type (e.g. JavaTimeModule for DateTime types,
     * or the primitive `int` deserializer) once and caches it. Benign data race on assignment: redundant
     * resolution yields an equivalent deserializer, never an incorrect one.
     */
    private fun FunSpec.Builder.addDelegateLookup(payloadType: TypeName) {
        addStatement(
            "val deser = innerDeserializer ?: ctx.findContextualValueDeserializer(ctx.constructType(%T::class.java), null).also { innerDeserializer = it }",
            payloadType
        )
    }

    /**
     * A payload read straight off the parser, guarded by the token check Jackson's own deserializer for
     * that payload type makes before its own direct read.
     *
     * @property condition true when the current token can be read as-is
     * @property read the read, valid only where [condition] holds
     */
    private class DirectRead(val condition: CodeBlock, val read: String)

    /**
     * The direct read for a primitive or `String` payload, so valid input skips the delegate call.
     * Any other token goes to Jackson's deserializer for the payload type, never to a lenient
     * `valueAs*`: those turn an object, array or boolean into `0`, `null` or `1` and leave the parser
     * inside the unconsumed value. Through the delegate, coercion config, `UNWRAP_SINGLE_VALUE_ARRAYS`
     * and `MismatchedInputException`s with the property path are Jackson's.
     *
     * `null` for `Char`: a direct read would need the same one-character-string check Jackson's `char`
     * deserializer starts with, so it would save nothing. `null` as well for every other payload, which
     * is always delegated.
     */
    private fun directRead(element: ValidatedKspGeneratorElement): DirectRead? {
        val jsonToken = generatorConfig.jsonToken()
        val intToken = CodeBlock.of("p.isExpectedNumberIntToken")
        val floatToken = CodeBlock.of("p.hasToken(%T.VALUE_NUMBER_FLOAT)", jsonToken)
        return when {
            // Exhaustive over Kind on purpose: no else arm, so adding a primitive kind is a compile
            // error here rather than a silent fall-through to the delegate.
            element.isPayloadPrimitive -> when (element.primitiveKind()) {
                Kind.INT -> DirectRead(intToken, "p.intValue")
                Kind.LONG -> DirectRead(intToken, "p.longValue")
                Kind.SHORT -> DirectRead(intToken, "p.shortValue")
                Kind.BYTE -> DirectRead(intToken, "p.byteValue")
                Kind.DOUBLE -> DirectRead(floatToken, "p.doubleValue")
                Kind.FLOAT -> DirectRead(floatToken, "p.floatValue")
                Kind.BOOLEAN -> DirectRead(
                    CodeBlock.of("p.hasToken(%T.VALUE_TRUE) || p.hasToken(%T.VALUE_FALSE)", jsonToken, jsonToken),
                    "p.booleanValue"
                )
                Kind.CHAR -> null
            }
            // Bypasses any user-customized String deserializer for string tokens; acceptable for
            // scalar wrapper payloads.
            isStringType(element.payloadType) -> DirectRead(
                CodeBlock.of("p.hasToken(%T.VALUE_STRING)", jsonToken),
                "p.${generatorConfig.parserStringProperty}"
            )
            else -> null
        }
    }

    /**
     * The payload's primitive kind, in a branch already guarded by [isPayloadPrimitive].
     *
     * Kept to one place so the two `when`s over [Kind] stay exhaustive rather than needing an else arm
     * for a case their guard has already ruled out.
     */
    private fun ValidatedKspGeneratorElement.primitiveKind(): Kind =
        (payload as Payload.Primitive).kind

    private fun isStringType(payloadType: KSType): Boolean {
        val fqn = payloadType.declaration.qualifiedName?.asString()
        return fqn == "kotlin.String" || fqn == "java.lang.String"
    }

    fun generateModule(
        context: Generator.Context,
        serializers: List<TypeSpec>,
        deserializers: List<TypeSpec>,
        elementTypes: List<ClassName>,
        isQuarkus: Boolean
    ): TypeSpec {
        val companionBuilder = TypeSpec.companionObjectBuilder()

        for (serializer in serializers) {
            val name = serializer.name!!
            val instanceName = name.replaceFirstChar { it.lowercase() }
            companionBuilder.addProperty(
                PropertySpec.builder(instanceName, ClassName("", name))
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("%T()", ClassName("", name))
                    .build()
            )
        }
        for (deserializer in deserializers) {
            val name = deserializer.name!!
            val instanceName = name.replaceFirstChar { it.lowercase() }
            companionBuilder.addProperty(
                PropertySpec.builder(instanceName, ClassName("", name))
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("%T()", ClassName("", name))
                    .build()
            )
        }

        val moduleBuilder = TypeSpec.classBuilder(generatorConfig.lazyvalJacksonModuleName)
            .superclass(generatorConfig.simpleModule())
            .addSuperclassConstructorParameter("%S, %T.unknownVersion()",
                generatorConfig.lazyvalJacksonModuleName,
                ClassName(generatorConfig.corePackage, "Version"))
            .addGeneratedAnnotation(generatorConfig.executingGenerator, context)
            .addType(companionBuilder.build())
            .addFunction(buildSetupModule(serializers, deserializers, elementTypes))

        if (isQuarkus) {
            moduleBuilder
                .addSuperinterface(ClassName("io.quarkus.jackson", "ObjectMapperCustomizer"))
                .addAnnotation(AnnotationSpec.builder(ClassName("jakarta.inject", "Singleton")).build())
                .addFunction(buildQuarkusCustomizer())
        }

        serializers.forEach { moduleBuilder.addType(it.toBuilder().addModifiers(KModifier.PRIVATE).build()) }
        deserializers.forEach { moduleBuilder.addType(it.toBuilder().addModifiers(KModifier.PRIVATE).build()) }

        return moduleBuilder.build()
    }

    private fun buildSetupModule(
        serializers: List<TypeSpec>,
        deserializers: List<TypeSpec>,
        elementTypes: List<ClassName>
    ): FunSpec {
        val builder = FunSpec.builder("setupModule")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("context", generatorConfig.setupContext())
            .addStatement("super.setupModule(context)")
            .addStatement("val sers = %T()", generatorConfig.simpleSerializers())
            .addStatement("val desers = %T()", generatorConfig.simpleDeserializers())

        for (i in serializers.indices) {
            val instanceName = serializers[i].name!!.replaceFirstChar { it.lowercase() }
            builder.addStatement("sers.addSerializer(%T::class.java, $instanceName)", elementTypes[i])
        }
        for (i in deserializers.indices) {
            val instanceName = deserializers[i].name!!.replaceFirstChar { it.lowercase() }
            builder.addStatement("desers.addDeserializer(%T::class.java, $instanceName)", elementTypes[i])
        }

        return builder
            .addStatement("context.addSerializers(sers)")
            .addStatement("context.addDeserializers(desers)")
            .build()
    }

    private fun buildQuarkusCustomizer(): FunSpec =
        FunSpec.builder("customize")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("objectMapper", generatorConfig.objectMapper())
            .addStatement("objectMapper.registerModule(this)")
            .build()
}
