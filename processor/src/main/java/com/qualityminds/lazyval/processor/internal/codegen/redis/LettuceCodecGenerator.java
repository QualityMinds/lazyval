package com.qualityminds.lazyval.processor.internal.codegen.redis;

import com.palantir.javapoet.*;
import com.qualityminds.lazyval.collections.NonEmptySet;
import com.qualityminds.lazyval.processor.internal.codegen.ConstantNames;
import com.qualityminds.lazyval.processor.internal.codegen.GeneratedStamp;
import com.qualityminds.lazyval.processor.spi.Generator;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;
import com.qualityminds.lazyval.processor.spi.StockGeneratorIds;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.stream.Stream;

import static com.qualityminds.lazyval.processor.internal.codegen.JavaPoetExprs.code;

/**
 * Generates {@code LazyvalLettuceCodecs}: one Lettuce {@code RedisCodec<T, T>} per domain-primitive, to
 * be composed per connection with {@code RedisCodec.of(keyCodec, valueCodec)}.
 * <p>
 * Lettuce has no fallback for unknown types, so the codecs define the bytes themselves: the payload as
 * UTF-8 text, written the way Spring Data Redis' store converters write it. Data can then be shared with
 * an application reading it through the Spring Data generator's {@code RedisSerializers}. Payload types
 * without such a text form are skipped with a warning — there is no central registry the generator owns,
 * so an application simply writes its own codec for them.
 */
// must only be public for ServiceLoader, but it is not part of the API
public class LettuceCodecGenerator implements Generator {

    private static final String OPTION_GENERATED_PACKAGE = "lazyval.redis.lettuce.package";
    private static final String HOLDER = "LazyvalLettuceCodecs";
    private static final String TEXT_CODEC = "TextCodec";
    private static final String BINARY_CODEC = "BinaryCodec";
    /**
     * Stands in for the payload while the factory call is built, then gets replaced by the parse
     * expression. {@code #} cannot occur in a Java identifier, so it never collides with a factory name.
     */
    private static final String PAYLOAD_PLACEHOLDER = "#payload#";

    private static final ClassName REDIS_CODEC = ClassName.get("io.lettuce.core.codec", "RedisCodec");
    private static final ClassName STRING_CODEC = ClassName.get("io.lettuce.core.codec", "StringCodec");
    private static final ClassName BYTE_BUFFER = ClassName.get("java.nio", "ByteBuffer");
    private static final TypeName BYTES = ArrayTypeName.of(TypeName.BYTE);
    private static final AnnotationSpec OVERRIDE_ANNOTATION = AnnotationSpec.builder(
            ClassName.get("java.lang", "Override")).build();

    /**
     * How a payload is written as text and read back — mirrors Spring Data Redis' {@code BinaryConverters}
     * and {@code Jsr310Converters}, so the bytes match. {@code $L} is the payload (format) or the text
     * (parse), {@code $T} the payload type.
     */
    private record Text(String format, String parse) {}

    private static final Text TO_STRING_PARSE = new Text("$L.toString()", "$T.parse($L)");

    private static final Map<String, Text> TEXT_FORMATS = Map.ofEntries(
            Map.entry("java.lang.String", new Text("$L", "$L")),
            Map.entry("int", new Text("String.valueOf($L)", "Integer.parseInt($L)")),
            Map.entry("java.lang.Integer", new Text("$L.toString()", "Integer.valueOf($L)")),
            Map.entry("long", new Text("String.valueOf($L)", "Long.parseLong($L)")),
            Map.entry("java.lang.Long", new Text("$L.toString()", "Long.valueOf($L)")),
            Map.entry("short", new Text("String.valueOf($L)", "Short.parseShort($L)")),
            Map.entry("java.lang.Short", new Text("$L.toString()", "Short.valueOf($L)")),
            Map.entry("byte", new Text("String.valueOf($L)", "Byte.parseByte($L)")),
            Map.entry("java.lang.Byte", new Text("$L.toString()", "Byte.valueOf($L)")),
            Map.entry("double", new Text("String.valueOf($L)", "Double.parseDouble($L)")),
            Map.entry("java.lang.Double", new Text("$L.toString()", "Double.valueOf($L)")),
            Map.entry("float", new Text("String.valueOf($L)", "Float.parseFloat($L)")),
            Map.entry("java.lang.Float", new Text("$L.toString()", "Float.valueOf($L)")),
            Map.entry("java.math.BigDecimal", new Text("$L.toString()", "new $T($L)")),
            Map.entry("java.math.BigInteger", new Text("$L.toString()", "new $T($L)")),
            // Spring Data Redis writes booleans as 1/0 and reads 1 or true
            Map.entry("boolean", new Text("$L ? \"1\" : \"0\"", "\"1\".equals($L) || \"true\".equalsIgnoreCase($L)")),
            Map.entry("java.lang.Boolean", new Text("$L ? \"1\" : \"0\"", "\"1\".equals($L) || \"true\".equalsIgnoreCase($L)")),
            Map.entry("java.util.UUID", new Text("$L.toString()", "$T.fromString($L)")),
            // epoch millis, as Spring Data Redis writes them
            Map.entry("java.util.Date", new Text("Long.toString($L.getTime())", "new $T(Long.parseLong($L))")),
            Map.entry("java.time.LocalDate", TO_STRING_PARSE),
            Map.entry("java.time.LocalDateTime", TO_STRING_PARSE),
            Map.entry("java.time.LocalTime", TO_STRING_PARSE),
            Map.entry("java.time.Instant", TO_STRING_PARSE),
            Map.entry("java.time.ZonedDateTime", TO_STRING_PARSE),
            Map.entry("java.time.OffsetDateTime", TO_STRING_PARSE),
            Map.entry("java.time.OffsetTime", TO_STRING_PARSE),
            Map.entry("java.time.Duration", TO_STRING_PARSE),
            Map.entry("java.time.Period", TO_STRING_PARSE),
            Map.entry("java.time.ZoneId", new Text("$L.toString()", "$T.of($L)"))
    );

    private static final Text ENUM = new Text("$L.name()", "$T.valueOf($L)");

    @Override
    public String generatorId() {
        return StockGeneratorIds.REDIS_LETTUCE_CODEC;
    }

    @Override
    public Set<String> requiredClasspath() {
        return Set.of("io.lettuce.core.codec.RedisCodec");
    }

    @Override
    public Set<String> supportedOptions() {
        return Set.of(OPTION_GENERATED_PACKAGE);
    }

    @Override
    public Stream<GeneratorResult> generate(NonEmptySet<ValidatedGeneratorElement> elements, Context context) {
        List<FieldSpec> codecs = new ArrayList<>();
        List<ValidatedGeneratorElement> supported = new ArrayList<>();
        boolean needsBinary = false;
        for (ValidatedGeneratorElement element : elements) {
            if (isBinary(element.payloadType())) {
                codecs.add(binaryCodec(element));
                needsBinary = true;
            } else {
                Optional<Text> text = textFor(element.payloadType());
                if (text.isEmpty()) {
                    context.logWarning(this, element.element(), element.name().canonicalName() + ": no Lettuce codec is generated, "
                            + "its payload type " + element.payloadType() + " has no text encoding; "
                            + "write a RedisCodec for it yourself");
                    continue;
                }
                codecs.add(textCodec(element, text.get()));
            }
            supported.add(element);
        }
        if (codecs.isEmpty() || !ConstantNames.unique(supported, context, this)) {
            return Stream.empty();
        }

        TypeSpec.Builder holder = TypeSpec.classBuilder(HOLDER)
                .addAnnotation(GeneratedStamp.forGenerator(LettuceCodecGenerator.class))
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addJavadoc("""
                        Lettuce {@code RedisCodec}s, one per domain-primitive, usable as key and as value codec:
                        <pre>{@code
                        RedisClient.create(uri).connect(RedisCodec.of(StringCodec.UTF8, $L.$L));
                        }</pre>
                        A domain-primitive is stored as its wrapped value in UTF-8 text — the same bytes Spring Data Redis
                        writes for that value.
                        <p>
                        Generated by the Lazyval annotation processor. Do not modify.
                        """, HOLDER, ConstantNames.of(supported.get(0)))
                .addFields(codecs)
                .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
                .addType(buildTextCodec());
        if (needsBinary) {
            holder.addType(buildBinaryCodec());
        }

        String codecPackage = context.generatorPackage(OPTION_GENERATED_PACKAGE, "boundary.persistence.redis");
        JavaFile file = JavaFile.builder(codecPackage, holder.build())
                .skipJavaLangImports(true)
                .build();
        return Stream.of(new GeneratorResult.Java(
                new GeneratorResult.Metadata(file.packageName(), file.typeSpec().name()),
                file.toString()));
    }

    private static boolean isBinary(TypeMirror payload) {
        return payload.getKind() == TypeKind.ARRAY && payload.toString().equals("byte[]");
    }

    private static Optional<Text> textFor(TypeMirror payload) {
        if (payload instanceof DeclaredType declared && declared.asElement().getKind() == ElementKind.ENUM) {
            return Optional.of(ENUM);
        }
        return Optional.ofNullable(TEXT_FORMATS.get(payload.toString()));
    }

    private static FieldSpec textCodec(ValidatedGeneratorElement element, Text text) {
        TypeName elementType = TypeName.get(element.element().asType());
        TypeName payloadType = TypeName.get(element.payloadType());

        CodeBlock formatted = render(text.format(), code(element.java().read("value")), payloadType);
        CodeBlock parsed = render(text.parse(), CodeBlock.of("text"), payloadType);

        TypeSpec codec = TypeSpec.anonymousClassBuilder("")
                .superclass(ParameterizedTypeName.get(ClassName.get("", TEXT_CODEC), elementType))
                .addMethod(MethodSpec.methodBuilder("format")
                        .addAnnotation(OVERRIDE_ANNOTATION)
                        .returns(String.class)
                        .addParameter(elementType, "value")
                        .addStatement("return $L", formatted)
                        .build())
                .addMethod(MethodSpec.methodBuilder("parse")
                        .addAnnotation(OVERRIDE_ANNOTATION)
                        .returns(elementType)
                        .addParameter(String.class, "text")
                        .addStatement("return $L", code(element.java().create(PAYLOAD_PLACEHOLDER), PAYLOAD_PLACEHOLDER, parsed))
                        .build())
                .build();
        return constant(element, elementType, codec);
    }

    /**
     * A {@link Text} template with its value — and, where it names one, the payload type. Indexed
     * arguments, since a template may use the value twice; JavaPoet rejects indexed arguments a
     * template leaves unused, hence the type only where it occurs.
     */
    private static CodeBlock render(String template, CodeBlock value, TypeName payloadType) {
        String indexed = template.replace("$L", "$1L").replace("$T", "$2T");
        return template.contains("$T") ? CodeBlock.of(indexed, value, payloadType) : CodeBlock.of(indexed, value);
    }

    private static FieldSpec binaryCodec(ValidatedGeneratorElement element) {
        TypeName elementType = TypeName.get(element.element().asType());

        TypeSpec codec = TypeSpec.anonymousClassBuilder("")
                .superclass(ParameterizedTypeName.get(ClassName.get("", BINARY_CODEC), elementType))
                .addMethod(MethodSpec.methodBuilder("format")
                        .addAnnotation(OVERRIDE_ANNOTATION)
                        .returns(BYTES)
                        .addParameter(elementType, "value")
                        .addStatement("return $L", code(element.java().read("value")))
                        .build())
                .addMethod(MethodSpec.methodBuilder("parse")
                        .addAnnotation(OVERRIDE_ANNOTATION)
                        .returns(elementType)
                        .addParameter(BYTES, "bytes")
                        .addStatement("return $L", code(element.java().create("bytes")))
                        .build())
                .build();
        return constant(element, elementType, codec);
    }

    private static FieldSpec constant(ValidatedGeneratorElement element, TypeName elementType, TypeSpec codec) {
        return FieldSpec.builder(ParameterizedTypeName.get(REDIS_CODEC, elementType, elementType),
                        ConstantNames.of(element), Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$L", codec)
                .build();
    }

    /** Keys and values alike: the payload's text form as UTF-8, through Lettuce's own {@code StringCodec}. */
    private static TypeSpec buildTextCodec() {
        TypeVariableName t = TypeVariableName.get("T");
        return TypeSpec.classBuilder(TEXT_CODEC)
                .addModifiers(Modifier.PRIVATE, Modifier.ABSTRACT, Modifier.STATIC)
                .addTypeVariable(t)
                .addSuperinterface(ParameterizedTypeName.get(REDIS_CODEC, t, t))
                .addMethod(MethodSpec.methodBuilder("format")
                        .addModifiers(Modifier.ABSTRACT)
                        .returns(String.class)
                        .addParameter(t, "value")
                        .build())
                .addMethod(MethodSpec.methodBuilder("parse")
                        .addModifiers(Modifier.ABSTRACT)
                        .returns(t)
                        .addParameter(String.class, "text")
                        .build())
                .addMethod(decode("decodeKey", t, CodeBlock.of("parse($T.UTF8.decodeKey(bytes))", STRING_CODEC)))
                .addMethod(decode("decodeValue", t, CodeBlock.of("parse($T.UTF8.decodeValue(bytes))", STRING_CODEC)))
                .addMethod(encode("encodeKey", "key", t, CodeBlock.of(
                        "$T.UTF8.encodeKey(key == null ? null : format(key))", STRING_CODEC)))
                .addMethod(encode("encodeValue", "value", t, CodeBlock.of(
                        "$T.UTF8.encodeValue(value == null ? null : format(value))", STRING_CODEC)))
                .build();
    }

    /** Keys and values alike: the payload's bytes unchanged. */
    private static TypeSpec buildBinaryCodec() {
        TypeVariableName t = TypeVariableName.get("T");
        return TypeSpec.classBuilder(BINARY_CODEC)
                .addModifiers(Modifier.PRIVATE, Modifier.ABSTRACT, Modifier.STATIC)
                .addTypeVariable(t)
                .addSuperinterface(ParameterizedTypeName.get(REDIS_CODEC, t, t))
                .addMethod(MethodSpec.methodBuilder("format")
                        .addModifiers(Modifier.ABSTRACT)
                        .returns(BYTES)
                        .addParameter(t, "value")
                        .build())
                .addMethod(MethodSpec.methodBuilder("parse")
                        .addModifiers(Modifier.ABSTRACT)
                        .returns(t)
                        .addParameter(BYTES, "bytes")
                        .build())
                .addMethod(decode("decodeKey", t, CodeBlock.of("parse(copy(bytes))")))
                .addMethod(decode("decodeValue", t, CodeBlock.of("parse(copy(bytes))")))
                .addMethod(encode("encodeKey", "key", t, CodeBlock.of(
                        "$T.wrap(key == null ? new byte[0] : format(key))", BYTE_BUFFER)))
                .addMethod(encode("encodeValue", "value", t, CodeBlock.of(
                        "$T.wrap(value == null ? new byte[0] : format(value))", BYTE_BUFFER)))
                .addMethod(MethodSpec.methodBuilder("copy")
                        .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                        .returns(BYTES)
                        .addParameter(BYTE_BUFFER, "buffer")
                        .addStatement("byte[] bytes = new byte[buffer.remaining()]")
                        .addStatement("buffer.get(bytes)")
                        .addStatement("return bytes")
                        .build())
                .build();
    }

    private static MethodSpec decode(String name, TypeVariableName t, CodeBlock body) {
        return MethodSpec.methodBuilder(name)
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .returns(t)
                .addParameter(BYTE_BUFFER, "bytes")
                .addStatement("return $L", body)
                .build();
    }

    private static MethodSpec encode(String name, String parameter, TypeVariableName t, CodeBlock body) {
        return MethodSpec.methodBuilder(name)
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .returns(BYTE_BUFFER)
                .addParameter(t, parameter)
                .addStatement("return $L", body)
                .build();
    }
}
