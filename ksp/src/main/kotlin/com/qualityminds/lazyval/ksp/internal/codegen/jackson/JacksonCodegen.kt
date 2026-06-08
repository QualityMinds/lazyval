package com.qualityminds.lazyval.ksp.internal.codegen.jackson

import com.qualityminds.lazyval.ksp.internal.codegen.GeneratedStamp.addGeneratedAnnotation
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

/**
 * Shared code-generation logic for Jackson serializer/deserializer modules.
 * Parameterized by [JacksonVersion] to handle Jackson 2 vs 3 differences.
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
internal class JacksonCodegen(private val version: JacksonVersion) {

    fun generateSerializer(element: ValidatedKspGeneratorElement): TypeSpec {
        val elementClassName = element.element.toClassName()
        val wrappedType = element.wrappedProperty
        val serializerName = "${element.typeName.name}Serializer"

        val wrappedTypeName = wrappedType.type.declaration.simpleName.asString()
        val serializeBody: FunSpec.Builder.() -> Unit = if (wrappedType.isPrimitive()) {
            {
                val writeStatement = when (wrappedTypeName) {
                    "Int", "Long", "Short" -> "gen.writeNumber(value.${element.kotlinAccessor})"
                    "Float", "Double" -> "gen.writeNumber(value.${element.kotlinAccessor})"
                    "Boolean" -> "gen.writeBoolean(value.${element.kotlinAccessor})"
                    "Char", "Byte" -> "gen.writeString(value.${element.kotlinAccessor})"
                    else -> "gen.writeString(value.${element.kotlinAccessor})"
                }
                addStatement(writeStatement)
            }
        } else {
            {
                // Delegate to the already-registered serializer (e.g. JavaTimeModule for DateTime types)
                addStatement(
                    "val ser = ctx.findValueSerializer(%T::class.java)",
                    wrappedType.type.toTypeName()
                )
                addStatement("ser.serialize(value.${element.kotlinAccessor}, gen, ctx)")
            }
        }

        return TypeSpec.classBuilder(serializerName)
            .superclass(version.stdSerializer().parameterizedBy(elementClassName))
            .addSuperclassConstructorParameter(CodeBlock.of("%T::class.java", elementClassName))
            .addFunction(
                FunSpec.builder("serialize")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("value", elementClassName)
                    .addParameter("gen", version.jsonGenerator())
                    .addParameter("ctx", version.serializerProvider())
                    .apply(serializeBody)
                    .build()
            )
            .build()
    }

    fun generateDeserializer(element: ValidatedKspGeneratorElement): TypeSpec {
        val elementClassName = element.element.toClassName()
        val wrappedType = element.wrappedProperty
        val deserializerName = "${element.typeName.name}Deserializer"

        val factoryReturnsNullable = element.factoryMethod?.returnType?.resolve()?.isMarkedNullable ?: false
        val returnType = elementClassName.copy(nullable = factoryReturnsNullable)

        val wrappedTypeName = wrappedType.type.declaration.simpleName.asString()

        val deserializeBody: FunSpec.Builder.() -> Unit = if (wrappedType.isPrimitive()) {
            {
                val readValueStatement = when (wrappedTypeName) {
                    "Int" -> "val value = p.valueAsInt"
                    "Long" -> "val value = p.valueAsLong"
                    "Double" -> "val value = p.valueAsDouble"
                    "Boolean" -> "val value = p.valueAsBoolean"
                    "Float" -> "val value = p.valueAsDouble.toFloat()"
                    "Short" -> "val value = p.valueAsInt.toShort()"
                    "Byte" -> "val value = p.valueAsInt.toByte()"
                    "Char" -> "val value = p.valueAsString[0]"
                    else -> "val value = p.valueAsString"
                }
                addStatement(readValueStatement)
                addStatement("return ${element.objectCreation("value")}")
            }
        } else {
            {
                // Delegate to the already-registered deserializer (e.g. JavaTimeModule for DateTime types)
                addStatement(
                    "val deser = ctx.findContextualValueDeserializer(ctx.constructType(%T::class.java), null)",
                    wrappedType.type.toTypeName()
                )
                addStatement("val value = deser.deserialize(p, ctx) as %T", wrappedType.type.toTypeName())
                addStatement("return ${element.objectCreation("value")}")
            }
        }

        val deserializeMethod = FunSpec.builder("deserialize")
            .addModifiers(KModifier.OVERRIDE)
            .returns(returnType)
            .addParameter("p", version.jsonParser())
            .addParameter("ctx", version.deserializationContext())
            .apply(deserializeBody)

        return TypeSpec.classBuilder(deserializerName)
            .superclass(version.stdDeserializer().parameterizedBy(returnType))
            .addSuperclassConstructorParameter(CodeBlock.of("%T::class.java", elementClassName))
            .addFunction(deserializeMethod.build())
            .build()
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

        val moduleBuilder = TypeSpec.classBuilder(version.lazyvalJacksonModuleName)
            .superclass(version.simpleModule())
            .addGeneratedAnnotation(version.executingGenerator, context)
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
            .addParameter("context", version.setupContext())
            .addStatement("super.setupModule(context)")
            .addStatement("val sers = %T()", version.simpleSerializers())
            .addStatement("val desers = %T()", version.simpleDeserializers())

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
            .addParameter("objectMapper", version.objectMapper())
            .addStatement("objectMapper.registerModule(this)")
            .build()
}
