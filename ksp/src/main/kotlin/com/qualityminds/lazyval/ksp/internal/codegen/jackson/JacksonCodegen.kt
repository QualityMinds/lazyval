package com.qualityminds.lazyval.ksp.internal.codegen.jackson

import com.qualityminds.lazyval.ksp.internal.codegen.GeneratedStamp.addGeneratedAnnotation
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.qualityminds.lazyval.ksp.spi.WrappedProperty
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
        val wrappedType = element.wrappedProperty
        val serializerName = "${element.typeName.name}Serializer"

        val wrappedTypeName = wrappedType.type.declaration.simpleName.asString()
        val needsCachedSerializer = !wrappedType.isPrimitive() && !isStringType(wrappedType)

        val serializeBody: FunSpec.Builder.() -> Unit = when {
            wrappedType.isPrimitive() -> {
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
            }
            isStringType(wrappedType) -> {
                {
                    // Direct write — avoids per-call serializer lookup. Bypasses any user-customized
                    // String serializer; acceptable for scalar wrapper payloads.
                    addStatement("gen.writeString(value.${element.kotlinAccessor})")
                }
            }
            else -> {
                {
                    // Resolve the delegate serializer (e.g. JavaTimeModule for DateTime types) once
                    // and cache it. Benign data race on assignment: redundant resolution yields an
                    // equivalent serializer, never an incorrect one.
                    addStatement(
                        "val ser = innerSerializer ?: ctx.findValueSerializer(%T::class.java).also { innerSerializer = it }",
                        wrappedType.type.toTypeName()
                    )
                    addStatement("ser.serialize(value.${element.kotlinAccessor}, gen, ctx)")
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
        val wrappedType = element.wrappedProperty
        val deserializerName = "${element.typeName.name}Deserializer"

        val factoryReturnsNullable = element.factoryMethod?.returnType?.resolve()?.isMarkedNullable ?: false
        val returnType = elementClassName.copy(nullable = factoryReturnsNullable)
        val needsCachedDeserializer = !wrappedType.isPrimitive() && !isStringType(wrappedType)

        val deserializeMethod = FunSpec.builder("deserialize")
            .addModifiers(KModifier.OVERRIDE)
            .returns(returnType)
            .addParameter("p", generatorConfig.jsonParser())
            .addParameter("ctx", generatorConfig.deserializationContext())
            .apply(deserializeBody(element, wrappedType))

        val builder = TypeSpec.classBuilder(deserializerName)
            .superclass(generatorConfig.stdDeserializer().parameterizedBy(returnType))
            .addSuperclassConstructorParameter(CodeBlock.of("%T::class.java", elementClassName))

        if (needsCachedDeserializer) {
            val cachedFieldType = generatorConfig.valueDeserializer().parameterizedBy(ANY).copy(nullable = true)
            builder.addProperty(
                PropertySpec.builder("innerDeserializer", cachedFieldType)
                    .addModifiers(KModifier.PRIVATE)
                    .mutable(true)
                    .initializer("null")
                    .build()
            )
        }

        return builder.addFunction(deserializeMethod.build()).build()
    }

    private fun deserializeBody(
        element: ValidatedKspGeneratorElement,
        wrappedType: WrappedProperty
    ): FunSpec.Builder.() -> Unit = when {
        wrappedType.isPrimitive() -> {
            {
                addStatement(primitiveReadStatement(wrappedType.type.declaration.simpleName.asString()))
                addStatement("return ${element.objectCreation("value")}")
            }
        }
        isStringType(wrappedType) -> {
            {
                // Direct read — avoids per-call deserializer lookup. Bypasses any user-customized
                // String deserializer; acceptable for scalar wrapper payloads.
                addStatement("val value = p.valueAsString")
                addStatement("return ${element.objectCreation("value")}")
            }
        }
        else -> {
            {
                // Resolve the delegate deserializer (e.g. JavaTimeModule for DateTime types) once
                // and cache it. Benign data race on assignment: redundant resolution yields an
                // equivalent deserializer, never an incorrect one.
                addStatement(
                    "val deser = innerDeserializer ?: ctx.findContextualValueDeserializer(ctx.constructType(%T::class.java), null).also { innerDeserializer = it }",
                    wrappedType.type.toTypeName()
                )
                addStatement("val value = deser.deserialize(p, ctx) as %T", wrappedType.type.toTypeName())
                addStatement("return ${element.objectCreation("value")}")
            }
        }
    }

    private fun primitiveReadStatement(wrappedTypeName: String): String = when (wrappedTypeName) {
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

    private fun isStringType(wrappedType: WrappedProperty): Boolean {
        val fqn = wrappedType.type.declaration.qualifiedName?.asString()
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
