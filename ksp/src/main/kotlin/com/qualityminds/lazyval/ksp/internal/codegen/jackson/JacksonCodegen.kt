package com.qualityminds.lazyval.ksp.internal.codegen.jackson

import com.qualityminds.lazyval.ksp.internal.codegen.kotlinPoet
import com.qualityminds.lazyval.ksp.internal.codegen.GeneratedStamp.addGeneratedAnnotation
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.naming.Payload
import com.qualityminds.lazyval.naming.Payload.Kind
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
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

        val serializeMethod = FunSpec.builder("serialize")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("value", elementClassName)
            .addParameter("gen", generatorConfig.jsonGenerator())
            .addParameter("ctx", generatorConfig.serializerProvider())
        val builder = TypeSpec.classBuilder(serializerName)
            .superclass(generatorConfig.stdSerializer().parameterizedBy(elementClassName))
            .addSuperclassConstructorParameter(CodeBlock.of("%T::class.java", elementClassName))

        if (element.isPayloadPrimitive) {
            // Exhaustive over Kind on purpose: no else arm, so adding a primitive kind is
            // a compile error here rather than a silent fall-through to writeString.
            val writeStatement = when (element.primitiveKind()) {
                Kind.INT, Kind.LONG, Kind.SHORT, Kind.FLOAT, Kind.DOUBLE ->
                    "gen.writeNumber(${element.kotlin.read("value")})"
                Kind.BOOLEAN -> "gen.writeBoolean(${element.kotlin.read("value")})"
                Kind.CHAR, Kind.BYTE -> "gen.writeString(${element.kotlin.read("value")}.toString())"
            }
            return builder.addFunction(serializeMethod.addStatement(writeStatement).build()).build()
        }

        serializeMethod.addStatement("checkNotNull(innerSerializer).serialize(${element.kotlin.read("value")}, gen, ctx)")
        val createContextual = contextualFactory(generatorConfig.valueSerializer(), generatorConfig.serializerProvider())
            .addStatement(
                "return %T(ctx.findContentValueSerializer(%T::class.java, property))",
                ClassName("", serializerName), element.payloadType.toTypeName()
            )
        if (generatorConfig.contextualViaInterface) {
            builder.addSuperinterface(generatorConfig.contextualSerializer())
        }
        return builder
            .withDelegate("innerSerializer", generatorConfig.valueSerializer())
            .addFunction(createContextual.build())
            .addFunction(serializeMethod.build())
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
        val createContextual = contextualFactory(generatorConfig.valueDeserializer(), generatorConfig.deserializationContext())
            .addStatement(
                "return %T(ctx.findContextualValueDeserializer(ctx.constructType(%T::class.java), property))",
                ClassName("", deserializerName), element.payloadType.toTypeName()
            )

        val builder = TypeSpec.classBuilder(deserializerName)
            .superclass(generatorConfig.stdDeserializer().parameterizedBy(returnType))
            .addSuperclassConstructorParameter(CodeBlock.of("%T::class.java", elementClassName))
        if (generatorConfig.contextualViaInterface) {
            builder.addSuperinterface(generatorConfig.contextualDeserializer())
        }
        return builder
            .withDelegate("innerDeserializer", generatorConfig.valueDeserializer())
            .addFunction(createContextual.build())
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
                addStatement("checkNotNull(innerDeserializer).deserialize(p, ctx) as %T", payloadType)
                endControlFlow()
            } else {
                addStatement("val value = checkNotNull(innerDeserializer).deserialize(p, ctx) as %T", payloadType)
            }
            addStatement("return %L", element.kotlin.create("value").kotlinPoet())
        }
    }

    /**
     * The delegate as a constructor property, `null` on the instance the module registers and set on
     * every instance [contextualFactory] returns.
     */
    private fun TypeSpec.Builder.withDelegate(name: String, valueType: ClassName): TypeSpec.Builder {
        val delegateType = valueType.parameterizedBy(ANY).copy(nullable = true)
        return primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(ParameterSpec.builder(name, delegateType).defaultValue("null").build())
                .build()
        ).addProperty(
            PropertySpec.builder(name, delegateType)
                .addModifiers(KModifier.PRIVATE)
                .initializer(name)
                .build()
        )
    }

    /**
     * `createContextual`, which Jackson calls on the registered instance for every mapper and every
     * property, before the first value is read or written. The (de)serializer it returns carries
     * Jackson's own (de)serializer for the payload type, resolved by that mapper for that property —
     * including whatever the application registered for the payload type, its config overrides, and a
     * `@JsonFormat` on the property. Resolved per call rather than cached on the shared instance, so
     * one mapper's resolution never reaches another.
     */
    private fun contextualFactory(valueType: ClassName, contextType: ClassName): FunSpec.Builder =
        FunSpec.builder("createContextual")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("ctx", contextType)
            .addParameter("property", generatorConfig.beanProperty().copy(nullable = true))
            .returns(valueType.parameterizedBy(STAR))

    /**
     * A payload read straight off the parser, guarded by the token check Jackson's own deserializer for
     * that payload type makes before its own direct read.
     *
     * @property condition true when the current token can be read as-is
     * @property read the read, valid only where [condition] holds
     */
    private class DirectRead(val condition: CodeBlock, val read: String)

    /**
     * The direct read for a primitive payload, so valid input skips the delegate call. Any other token
     * goes to Jackson's deserializer for the payload type, never to a lenient `valueAs*`: those turn an
     * object, array or boolean into `0` or `1` and leave the parser inside the unconsumed value. Through
     * the delegate, coercion config, `UNWRAP_SINGLE_VALUE_ARRAYS` and `MismatchedInputException`s with the
     * property path are Jackson's.
     *
     * `null` for `Char`: a direct read would need the same one-character-string check Jackson's `char`
     * deserializer starts with, so it would save nothing. `null` for `String` as well, so that a String
     * deserializer the application registers — trimming, sanitizing, rejecting control characters —
     * applies to String payloads too. Every other payload is always delegated.
     */
    private fun directRead(element: ValidatedKspGeneratorElement): DirectRead? {
        if (!element.isPayloadPrimitive) return null
        val jsonToken = generatorConfig.jsonToken()
        val intToken = CodeBlock.of("p.isExpectedNumberIntToken")
        val floatToken = CodeBlock.of("p.hasToken(%T.VALUE_NUMBER_FLOAT)", jsonToken)
        // Exhaustive over Kind on purpose: no else arm, so adding a primitive kind is a compile
        // error here rather than a silent fall-through to the delegate.
        return when (element.primitiveKind()) {
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
    }

    /**
     * The payload's primitive kind, in a branch already guarded by [isPayloadPrimitive].
     *
     * Kept to one place so the two `when`s over [Kind] stay exhaustive rather than needing an else arm
     * for a case their guard has already ruled out.
     */
    private fun ValidatedKspGeneratorElement.primitiveKind(): Kind =
        (payload as Payload.Primitive).kind

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
