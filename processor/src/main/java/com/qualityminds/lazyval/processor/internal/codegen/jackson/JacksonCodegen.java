package com.qualityminds.lazyval.processor.internal.codegen.jackson;

import com.palantir.javapoet.*;
import com.qualityminds.lazyval.processor.internal.codegen.GeneratedStamp;
import com.qualityminds.lazyval.processor.spi.Generator;
import com.qualityminds.lazyval.naming.Payload;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.Optional;
import static com.qualityminds.lazyval.processor.internal.codegen.JavaPoetExprs.code;

/**
 * Shared code-generation logic for Jackson serializer/deserializer modules.
 * Parameterized by {@link GeneratorConfig} to handle Jackson 2 vs 3 differences.
 *
 * <h3>Null invariants</h3>
 * <b>Serializer:</b> Jackson never calls {@code serialize} for a {@code null} value; null is
 * intercepted upstream at the property-binding level. The generated {@code serialize(DomainType)}
 * method therefore always receives a non-null argument and does not need to guard against null.
 * <p>
 * <b>Deserializer:</b> Jackson never calls {@code deserialize} for a JSON {@code null} token;
 * null tokens are resolved upstream before the deserializer is invoked. The factory method is
 * therefore always called with a non-null raw value.
 * Java's type system provides no compile-time guarantee: if the factory returns {@code null}
 * for a non-null input (e.g. a blank-string guard), Jackson sets {@code null} on the target
 * field silently without throwing. If the target field is a non-nullable type, a
 * {@code NullPointerException} will be thrown at access time, not during deserialization.
 */
final class JacksonCodegen {

    private static final AnnotationSpec OVERRIDE_ANNOTATION = AnnotationSpec.builder(
            ClassName.get("java.lang", "Override")).build();

    private final GeneratorConfig generatorConfig;

    JacksonCodegen(GeneratorConfig generatorConfig) {
        this.generatorConfig = generatorConfig;
    }

    TypeSpec generateSerializer(ValidatedGeneratorElement element) {
        var elementType = TypeName.get(element.element().asType());
        var serializerName = element.name().flatName() + "Serializer";

        var serializeMethod = MethodSpec.methodBuilder("serialize")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.VOID)
                .addParameter(elementType, "value")
                .addParameter(generatorConfig.jsonGenerator(), "gen")
                .addParameter(generatorConfig.serializerProvider(), "provider");

        if (generatorConfig.deserializerDeclaresExceptions()) {
            serializeMethod.addException(ClassName.get("java.io", "IOException"));
        }

        var builder = TypeSpec.classBuilder(serializerName)
                .superclass(ParameterizedTypeName.get(generatorConfig.stdSerializer(), elementType));

        if (element.payload() instanceof Payload.Primitive primitive) {
            // Exhaustive over Kind on purpose: no default arm, so adding a primitive kind is a
            // compile error here rather than a silent fall-through to writeString.
            String writeStatement = switch (primitive.kind()) {
                case INT, LONG, SHORT, FLOAT, DOUBLE -> "gen.writeNumber($L)";
                case BOOLEAN -> "gen.writeBoolean($L)";
                case CHAR, BYTE -> "gen.writeString(String.valueOf($L))";
            };
            serializeMethod.addStatement(writeStatement, code(element.java().read("value")));
            return builder
                    .addField(instanceField(serializerName, ""))
                    .addMethod(MethodSpec.constructorBuilder()
                            .addModifiers(Modifier.PROTECTED)
                            .addStatement("super($T.class)", elementType).build())
                    .addMethod(serializeMethod.build())
                    .build();
        }

        var delegateType = ParameterizedTypeName.get(generatorConfig.valueSerializer(), ClassName.OBJECT);
        serializeMethod.addStatement("innerSerializer.serialize($L, gen, provider)", code(element.java().read("value")));
        var createContextual = contextualFactory(generatorConfig.valueSerializer(), generatorConfig.serializerProvider(),
                "provider")
                .addStatement("return new $L(provider.findContentValueSerializer($T.class, property))",
                        serializerName, TypeName.get(element.payloadType()));
        if (generatorConfig.contextualViaInterface()) {
            builder.addSuperinterface(generatorConfig.contextualSerializer());
        }
        return builder
                .addField(instanceField(serializerName, "null"))
                .addField(FieldSpec.builder(delegateType, "innerSerializer", Modifier.PRIVATE, Modifier.FINAL).build())
                .addMethod(delegatingConstructor(elementType, delegateType, "innerSerializer"))
                .addMethod(createContextual.build())
                .addMethod(serializeMethod.build())
                .build();
    }

    TypeSpec generateDeserializer(ValidatedGeneratorElement element) {
        var elementType = TypeName.get(element.element().asType());
        var deserializerName = element.name().flatName() + "Deserializer";

        var deserializeMethod = MethodSpec.methodBuilder("deserialize")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .returns(elementType)
                .addParameter(generatorConfig.jsonParser(), "p")
                .addParameter(generatorConfig.deserializationContext(), "ctxt");

        if (generatorConfig.deserializerDeclaresExceptions()) {
            deserializeMethod
                    .addException(ClassName.get("java.io", "IOException"))
                    .addException(ClassName.get(generatorConfig.corePackage(), "JacksonException"));
        }

        // Declared with the payload type rather than var: from the delegate, a char payload would
        // otherwise be a Character, and bind to a factory overload taking Character or Object.
        var payloadType = TypeName.get(element.payloadType());
        var directRead = directRead(element);
        if (directRead.isPresent()) {
            deserializeMethod
                    .addStatement("$T value", payloadType)
                    .beginControlFlow("if ($L)", directRead.get().condition())
                    .addStatement("value = $L", directRead.get().read())
                    .nextControlFlow("else")
                    .addStatement("value = ($T) innerDeserializer.deserialize(p, ctxt)", payloadType.box())
                    .endControlFlow();
        } else {
            deserializeMethod.addStatement("$T value = ($T) innerDeserializer.deserialize(p, ctxt)",
                    payloadType, payloadType.box());
        }
        deserializeMethod.addStatement("return $L", code(element.java().create("value")));

        var delegateType = ParameterizedTypeName.get(generatorConfig.valueDeserializer(), ClassName.OBJECT);
        var createContextual = contextualFactory(generatorConfig.valueDeserializer(),
                generatorConfig.deserializationContext(), "ctxt")
                .addStatement("return new $L(ctxt.findContextualValueDeserializer(ctxt.constructType($T.class), property))",
                        deserializerName, payloadType);
        var builder = TypeSpec.classBuilder(deserializerName)
                .superclass(ParameterizedTypeName.get(generatorConfig.stdDeserializer(), elementType));
        if (generatorConfig.contextualViaInterface()) {
            builder.addSuperinterface(generatorConfig.contextualDeserializer());
        }
        return builder
                .addField(instanceField(deserializerName, "null"))
                .addField(FieldSpec.builder(delegateType, "innerDeserializer", Modifier.PRIVATE, Modifier.FINAL).build())
                .addMethod(delegatingConstructor(elementType, delegateType, "innerDeserializer"))
                .addMethod(createContextual.build())
                .addMethod(deserializeMethod.build())
                .build();
    }

    /** The instance the module registers; for a delegating (de)serializer, one that has no delegate yet. */
    private static FieldSpec instanceField(String typeName, String constructorArgument) {
        return FieldSpec.builder(ClassName.bestGuess(typeName), "INSTANCE", Modifier.STATIC, Modifier.FINAL)
                .initializer("new $L($L)", typeName, constructorArgument)
                .build();
    }

    private static MethodSpec delegatingConstructor(TypeName elementType, TypeName delegateType, String delegate) {
        return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PROTECTED)
                .addParameter(delegateType, delegate)
                .addStatement("super($T.class)", elementType)
                .addStatement("this.$N = $N", delegate, delegate)
                .build();
    }

    /**
     * {@code createContextual}, which Jackson calls on the registered instance for every mapper and every
     * property, before the first value is read or written. The (de)serializer it returns carries
     * Jackson's own (de)serializer for the payload type, resolved by that mapper for that property —
     * including whatever the application registered for the payload type, its config overrides, and a
     * {@code @JsonFormat} on the property. Resolved per call rather than cached on the shared
     * instance, so one mapper's resolution never reaches another.
     */
    private MethodSpec.Builder contextualFactory(ClassName valueType, ClassName contextType, String contextName) {
        var builder = MethodSpec.methodBuilder("createContextual")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(valueType, WildcardTypeName.subtypeOf(Object.class)))
                .addParameter(contextType, contextName)
                .addParameter(generatorConfig.beanProperty(), "property");
        if (generatorConfig.contextualViaInterface()) {
            builder.addException(generatorConfig.jsonMappingException());
        }
        return builder;
    }

    /**
     * A payload read straight off the parser, guarded by the token check Jackson's own deserializer for that
     * payload type makes before its own direct read.
     *
     * @param condition true when the current token can be read as-is
     * @param read      the read, valid only where {@code condition} holds
     */
    private record DirectRead(CodeBlock condition, String read) {}

    /**
     * The direct read for a primitive payload, so valid input skips the delegate call. Any other token
     * goes to Jackson's deserializer for the payload type, never to a lenient {@code getValueAs*()}: those
     * turn an object, array or boolean into {@code 0} or {@code 1} and leave the parser inside the
     * unconsumed value. Through the delegate, coercion config, {@code UNWRAP_SINGLE_VALUE_ARRAYS} and
     * {@code MismatchedInputException}s with the property path are Jackson's.
     * <p>
     * Empty for {@code char}: a direct read would need the same one-character-string check Jackson's
     * {@code char} deserializer starts with, so it would save nothing. Empty for {@code String} as well, so
     * that a String deserializer the application registers — trimming, sanitizing, rejecting control
     * characters — applies to String payloads too. Every other payload is always delegated.
     */
    private Optional<DirectRead> directRead(ValidatedGeneratorElement element) {
        if (!(element.payload() instanceof Payload.Primitive primitive)) {
            return Optional.empty();
        }
        var jsonToken = generatorConfig.jsonToken();
        var intToken = CodeBlock.of("p.isExpectedNumberIntToken()");
        var floatToken = CodeBlock.of("p.hasToken($T.VALUE_NUMBER_FLOAT)", jsonToken);
        // Exhaustive over Kind on purpose: no default arm, so adding a primitive kind is a
        // compile error here rather than a silent fall-through to the delegate.
        return switch (primitive.kind()) {
            case INT -> Optional.of(new DirectRead(intToken, "p.getIntValue()"));
            case LONG -> Optional.of(new DirectRead(intToken, "p.getLongValue()"));
            case SHORT -> Optional.of(new DirectRead(intToken, "p.getShortValue()"));
            case BYTE -> Optional.of(new DirectRead(intToken, "p.getByteValue()"));
            case DOUBLE -> Optional.of(new DirectRead(floatToken, "p.getDoubleValue()"));
            case FLOAT -> Optional.of(new DirectRead(floatToken, "p.getFloatValue()"));
            case BOOLEAN -> Optional.of(new DirectRead(
                    CodeBlock.of("p.hasToken($T.VALUE_TRUE) || p.hasToken($T.VALUE_FALSE)", jsonToken, jsonToken),
                    "p.getBooleanValue()"));
            case CHAR -> Optional.empty();
        };
    }

    TypeSpec generateModule(List<TypeSpec> serializers, List<TypeSpec> deserializers,
                            List<TypeName> elementTypes, Generator.Context context) {
        boolean isQuarkus = context.isOnClasspath("io.quarkus.jackson.ObjectMapperCustomizer");

        var moduleBuilder = TypeSpec.classBuilder(generatorConfig.lazyvalJacksonModuleName())
                .addAnnotation(GeneratedStamp.forGenerator(generatorConfig.executingGenerator()))
                .addModifiers(Modifier.PUBLIC)
                .superclass(generatorConfig.simpleModule())
                .addMethod(buildModuleConstructor())
                .addMethod(buildSetupModule(serializers, deserializers, elementTypes));

        if (isQuarkus) {
            moduleBuilder
                    .addSuperinterface(ClassName.get("io.quarkus.jackson", "ObjectMapperCustomizer"))
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("jakarta.inject", "Singleton")).build())
                    .addMethod(buildQuarkusCustomizer());
        }

        serializers.forEach(s -> moduleBuilder.addType(s.toBuilder().addModifiers(Modifier.STATIC).build()));
        deserializers.forEach(d -> moduleBuilder.addType(d.toBuilder().addModifiers(Modifier.STATIC).build()));

        return moduleBuilder.build();
    }

    private MethodSpec buildModuleConstructor() {
        var versionClass = ClassName.get(generatorConfig.corePackage(), "Version");
        return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement("super($S, $T.unknownVersion())", generatorConfig.lazyvalJacksonModuleName(), versionClass)
                .build();
    }

    private MethodSpec buildSetupModule(List<TypeSpec> serializers, List<TypeSpec> deserializers,
                                        List<TypeName> elementTypes) {
        var builder = MethodSpec.methodBuilder("setupModule")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(generatorConfig.setupContext(), "context")
                .returns(TypeName.VOID)
                .addStatement("super.setupModule(context)")
                .addStatement("$T sers = new $T()", generatorConfig.simpleSerializers(), generatorConfig.simpleSerializers())
                .addStatement("$T desers = new $T()", generatorConfig.simpleDeserializers(), generatorConfig.simpleDeserializers());

        for (int i = 0; i < serializers.size(); i++) {
            builder.addStatement("sers.addSerializer($T.class, $L.INSTANCE)",
                    elementTypes.get(i), serializers.get(i).name());
        }
        for (int i = 0; i < deserializers.size(); i++) {
            builder.addStatement("desers.addDeserializer($T.class, $L.INSTANCE)",
                    elementTypes.get(i), deserializers.get(i).name());
        }

        return builder
                .addStatement("context.addSerializers(sers)")
                .addStatement("context.addDeserializers(desers)")
                .build();
    }

    private MethodSpec buildQuarkusCustomizer() {
        return MethodSpec.methodBuilder("customize")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(generatorConfig.objectMapper(), "objectMapper")
                .returns(TypeName.VOID)
                .addStatement("objectMapper.registerModule(this)")
                .build();
    }
}
