package com.qualityminds.lazyval.processor.internal.codegen.jackson;

import com.palantir.javapoet.*;
import com.qualityminds.lazyval.processor.internal.codegen.GeneratedStamp;
import com.qualityminds.lazyval.processor.spi.Generator;
import com.qualityminds.lazyval.naming.Payload;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.Modifier;
import java.util.List;
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

        boolean needsCachedSerializer = false;
        if (element.payload() instanceof Payload.Primitive primitive) {
            // Exhaustive over Kind on purpose: no default arm, so adding a primitive kind is a
            // compile error here rather than a silent fall-through to writeString.
            String writeStatement = switch (primitive.kind()) {
                case INT, LONG, SHORT, FLOAT, DOUBLE -> "gen.writeNumber($L)";
                case BOOLEAN -> "gen.writeBoolean($L)";
                case CHAR, BYTE -> "gen.writeString(String.valueOf($L))";
            };
            serializeMethod.addStatement(writeStatement, code(element.java().read("value")));
        } else if (isStringPayload(element)) {
            // Direct write — avoids per-call serializer lookup. Bypasses any user-customized
            // String serializer; acceptable for scalar wrapper payloads.
            serializeMethod.addStatement("gen.writeString($L)", code(element.java().read("value")));
        } else {
            // Resolve the delegate serializer (e.g. JavaTimeModule for DateTime types) once
            // and cache it. Benign data race on assignment: redundant resolution yields an
            // equivalent serializer, never an incorrect one.
            needsCachedSerializer = true;
            var cachedFieldType = ParameterizedTypeName.get(generatorConfig.valueSerializer(), ClassName.OBJECT);
            serializeMethod
                    .addStatement("$T ser = this.innerSerializer", cachedFieldType)
                    .beginControlFlow("if (ser == null)")
                    .addStatement("ser = provider.findValueSerializer($T.class)", TypeName.get(element.payloadType()))
                    .addStatement("this.innerSerializer = ser")
                    .endControlFlow()
                    .addStatement("ser.serialize($L, gen, provider)", code(element.java().read("value")));
        }

        var builder = TypeSpec.classBuilder(serializerName)
                .superclass(ParameterizedTypeName.get(generatorConfig.stdSerializer(), elementType))
                .addField(FieldSpec.builder(
                                ClassName.bestGuess(serializerName), "INSTANCE",
                                Modifier.STATIC, Modifier.FINAL)
                        .initializer("new $L()", serializerName).build())
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PROTECTED)
                        .addStatement("super($T.class)", elementType).build())
                .addMethod(serializeMethod.build());

        if (needsCachedSerializer) {
            builder.addField(FieldSpec.builder(
                            ParameterizedTypeName.get(generatorConfig.valueSerializer(), ClassName.OBJECT),
                            "innerSerializer",
                            Modifier.PRIVATE)
                    .build());
        }

        return builder.build();
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

        boolean needsCachedDeserializer = false;
        if (element.payload() instanceof Payload.Primitive primitive) {
            String readValueStatement = switch (primitive.kind()) {
                case INT -> "var value = p.getValueAsInt()";
                case LONG -> "var value = p.getValueAsLong()";
                case DOUBLE -> "var value = p.getValueAsDouble()";
                case BOOLEAN -> "var value = p.getValueAsBoolean()";
                case FLOAT -> "var value = (float) p.getValueAsDouble()";
                case SHORT -> "var value = (short) p.getValueAsInt()";
                case BYTE -> "var value = (byte) p.getValueAsInt()";
                case CHAR -> "var value = p.getValueAsString().charAt(0)";
            };
            deserializeMethod.addStatement(readValueStatement);
        } else if (isStringPayload(element)) {
            // Direct read — avoids per-call deserializer lookup. Bypasses any user-customized
            // String deserializer; acceptable for scalar wrapper payloads.
            deserializeMethod.addStatement("var value = p.getValueAsString()");
        } else {
            // Resolve the delegate deserializer (e.g. JavaTimeModule for DateTime types) once
            // and cache it. Benign data race on assignment: redundant resolution yields an
            // equivalent deserializer, never an incorrect one.
            needsCachedDeserializer = true;
            var cachedFieldType = ParameterizedTypeName.get(generatorConfig.valueDeserializer(), ClassName.OBJECT);
            deserializeMethod
                    .addStatement("$T deser = this.innerDeserializer", cachedFieldType)
                    .beginControlFlow("if (deser == null)")
                    .addStatement("deser = ctxt.findContextualValueDeserializer(ctxt.constructType($T.class), null)",
                            TypeName.get(element.payloadType()))
                    .addStatement("this.innerDeserializer = deser")
                    .endControlFlow()
                    .addStatement("var value = ($T) deser.deserialize(p, ctxt)",
                            TypeName.get(element.payloadType()));
        }

        deserializeMethod
                .addStatement("return $L", code(element.java().create("value")));

        var builder = TypeSpec.classBuilder(deserializerName)
                .superclass(ParameterizedTypeName.get(generatorConfig.stdDeserializer(), elementType))
                .addField(FieldSpec.builder(
                                ClassName.bestGuess(deserializerName), "INSTANCE",
                                Modifier.STATIC, Modifier.FINAL)
                        .initializer("new $L()", deserializerName).build())
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PROTECTED)
                        .addStatement("super($T.class)", elementType).build())
                .addMethod(deserializeMethod.build());

        if (needsCachedDeserializer) {
            builder.addField(FieldSpec.builder(
                            ParameterizedTypeName.get(generatorConfig.valueDeserializer(), ClassName.OBJECT),
                            "innerDeserializer",
                            Modifier.PRIVATE)
                    .build());
        }

        return builder.build();
    }

    private static boolean isStringPayload(ValidatedGeneratorElement element) {
        return "java.lang.String".equals(element.payloadType().toString());
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
