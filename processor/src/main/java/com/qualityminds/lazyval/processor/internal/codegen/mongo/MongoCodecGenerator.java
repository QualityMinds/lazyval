package com.qualityminds.lazyval.processor.internal.codegen.mongo;

import com.palantir.javapoet.*;
import com.qualityminds.lazyval.collections.NonEmptySet;
import com.qualityminds.lazyval.processor.internal.codegen.GeneratedStamp;
import com.qualityminds.lazyval.processor.spi.Generator;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;
import com.qualityminds.lazyval.processor.spi.StockGeneratorIds;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.stream.Stream;
import static com.qualityminds.lazyval.processor.internal.codegen.JavaPoetExprs.code;

/**
 * Generates a native MongoDB driver {@code Codec} for each domain-primitive, grouped into a
 * {@code LazyvalMongoCodecs} class that implements {@code
 * org.bson.codecs.configuration.CodecProvider CodecProvider}. The provider resolves each
 * primitive's inner-type codec from the supplied {@code CodecRegistry} on demand and
 * delegates {@code encode}/{@code decode} to it, which transparently picks up whatever
 * representation the registry has configured for the payload type (e.g. UUID representation,
 * date/time codecs).
 *
 * <h3>Null handling — Mongo driver convention</h3>
 * The generated codecs follow the MongoDB Java driver convention: property-level codecs
 * operate on non-null values and assume the {@code org.bson.BsonReader} is positioned on a
 * non-null BSON token. This matches how the driver's own stock codecs
 * ({@code StringCodec}, {@code IntegerCodec}, {@code DateCodec}, ...) behave — none of them
 * null-guard their {@code encode}/{@code decode}.
 *
 * <p>The standard call paths all pre-filter BSON {@code NULL} before invoking property codecs:
 * <ul>
 *   <li>{@code PojoCodec} writes {@code writeNull()} directly for null fields on encode and
 *       sets the property to {@code null} without invoking the codec on decode.</li>
 *   <li>{@code IterableCodec}, {@code MapCodec}, array codecs apply the same filter at the
 *       element level.</li>
 * </ul>
 *
 * <p><b>Garbage-in / garbage-out.</b> Invoking {@code encode} with a {@code null} value, or
 * {@code decode} on a reader positioned at a BSON {@code NULL} token, is a contract violation
 * by the caller. The exact behavior in that case — {@link NullPointerException},
 * {@code BsonInvalidOperationException} from the inner reader, or a domain
 * {@code IllegalArgumentException} from the wrapper's factory — is intentionally undefined
 * and depends on the inner codec and the wrapper type. Callers using non-standard direct
 * codec lookups are responsible for filtering BSON nulls themselves.
 *
 * <h3>Quarkus integration</h3>
 * When the {@code quarkus-mongodb-client} extension is detected on the classpath, a
 * {@code LazyvalMongoCodecRegistrar} {@code @ApplicationScoped} {@code CodecProvider} bean
 * is also generated. Quarkus auto-discovers {@code CodecProvider} CDI beans and chains them
 * into the default Mongo registry — no further wiring is needed.
 */
// must only be public for ServiceLoader, but it is not part of the API
public class MongoCodecGenerator implements Generator {

    private static final String OPTION_GENERATED_PACKAGE = "lazyval.mongodb.package";
    private static final String OPTION_USER_CODECS = "lazyval.mongodb.codecs";
    private static final String OPTION_QUARKUS_REGISTER = "lazyval.mongodb.quarkus.register";

    private static final String CODEC_FQN = "org.bson.codecs.Codec";
    private static final String MONGO_CLIENT_SETTINGS_FQN = "com.mongodb.MongoClientSettings";
    private static final String QUARKUS_MONGO_MARKER = "io.quarkus.mongodb.MongoClientName";

    private static final ClassName CODEC = ClassName.get("org.bson.codecs", "Codec");
    private static final ClassName BSON_READER = ClassName.get("org.bson", "BsonReader");
    private static final ClassName BSON_WRITER = ClassName.get("org.bson", "BsonWriter");
    private static final ClassName ENCODER_CONTEXT = ClassName.get("org.bson.codecs", "EncoderContext");
    private static final ClassName DECODER_CONTEXT = ClassName.get("org.bson.codecs", "DecoderContext");
    private static final ClassName CODEC_REGISTRY = ClassName.get("org.bson.codecs.configuration", "CodecRegistry");
    private static final ClassName CODEC_REGISTRIES = ClassName.get("org.bson.codecs.configuration", "CodecRegistries");
    private static final ClassName CODEC_PROVIDER = ClassName.get("org.bson.codecs.configuration", "CodecProvider");
    private static final ClassName MONGO_CLIENT_SETTINGS = ClassName.get("com.mongodb", "MongoClientSettings");
    private static final ClassName SYSTEM = ClassName.get("java.lang", "System");
    private static final ClassName SYSTEM_LOGGER = ClassName.get("java.lang", "System", "Logger");
    private static final ClassName SYSTEM_LOGGER_LEVEL = ClassName.get("java.lang", "System", "Logger", "Level");
    private static final ClassName JAVA_LANG_CLASS = ClassName.get("java.lang", "Class");

    private static final AnnotationSpec OVERRIDE_ANNOTATION = AnnotationSpec.builder(
            ClassName.get("java.lang", "Override")).build();
    private static final AnnotationSpec SUPPRESS_UNCHECKED = AnnotationSpec.builder(SuppressWarnings.class)
            .addMember("value", "$S", "unchecked")
            .build();

    private static final String CODECS_CLASS_NAME = "LazyvalMongoCodecs";
    private static final String REGISTRAR_CLASS_NAME = "LazyvalMongoCodecRegistrar";

    @Override
    public String generatorId() {
        return StockGeneratorIds.MONGODB_CODEC;
    }

    @Override
    public Set<String> requiredClasspath() {
        return Set.of(CODEC_FQN);
    }

    @Override
    public Set<String> supportedOptions() {
        return Set.of(OPTION_GENERATED_PACKAGE, OPTION_USER_CODECS, OPTION_QUARKUS_REGISTER);
    }

    @Override
    public Stream<GeneratorResult> generate(NonEmptySet<ValidatedGeneratorElement> elements, Context context) {
        final String codecPackage = context.generatorPackage(OPTION_GENERATED_PACKAGE, "boundary.persistence.mongodb");

        List<String> userCodecFqns = validateUserCodecs(context, codecPackage);

        List<ValidatedGeneratorElement> orderedElements = new ArrayList<>();
        List<TypeSpec> codecSpecs = new ArrayList<>();
        for (ValidatedGeneratorElement element : elements) {
            orderedElements.add(element);
            codecSpecs.add(buildCodec(element));
        }

        List<GeneratorResult> results = new ArrayList<>();

        boolean hasMongoDriverCore = context.isOnClasspath(MONGO_CLIENT_SETTINGS_FQN);
        TypeSpec utilitySpec = buildCodecsUtility(orderedElements, codecSpecs, userCodecFqns, hasMongoDriverCore);
        JavaFile utilityFile = JavaFile.builder(codecPackage, utilitySpec)
                .skipJavaLangImports(true)
                .build();
        results.add(new GeneratorResult.Java(
                new GeneratorResult.Metadata(utilityFile.packageName(), utilityFile.typeSpec().name()),
                utilityFile.toString()));

        boolean isQuarkus = context.isOnClasspath(QUARKUS_MONGO_MARKER);
        boolean quarkusRegister = context.getSetting(OPTION_QUARKUS_REGISTER)
                .map(v -> !"false".equalsIgnoreCase(v))
                .orElse(true);

        if (isQuarkus && quarkusRegister) {
            TypeSpec registrarSpec = buildQuarkusRegistrar(codecPackage);
            JavaFile registrarFile = JavaFile.builder(codecPackage, registrarSpec)
                    .skipJavaLangImports(true)
                    .build();
            results.add(new GeneratorResult.Java(
                    new GeneratorResult.Metadata(registrarFile.packageName(), registrarFile.typeSpec().name()),
                    registrarFile.toString()));
        }
        return results.stream();
    }

    private List<String> validateUserCodecs(Context context, String codecPackage) {
        String raw = context.getSetting(OPTION_USER_CODECS).orElse("");
        if (raw.isBlank()) {
            return List.of();
        }
        List<String> fqns = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        List<String> valid = new ArrayList<>(fqns.size());
        for (String fqn : fqns) {
            Optional<Context.ClassInspection> inspection = context.inspectClass(fqn);
            if (inspection.isEmpty()) {
                context.logError(this, OPTION_USER_CODECS + ": class '" + fqn + "' not found on compile classpath");
                continue;
            }
            Context.ClassInspection info = inspection.get();
            boolean ok = true;
            if (!info.isAssignableTo(CODEC_FQN)) {
                context.logError(this, OPTION_USER_CODECS + ": class '" + fqn + "' does not implement " + CODEC_FQN);
                ok = false;
            }
            if (!info.isAccessibleFrom(codecPackage)) {
                context.logError(this, OPTION_USER_CODECS + ": class '" + fqn + "' is not accessible from the generated codecs at package '" + codecPackage + "'");
                ok = false;
            }
            if (!info.hasAccessibleNoArgConstructor(codecPackage)) {
                context.logError(this, OPTION_USER_CODECS + ": class '" + fqn + "' must declare a no-arg constructor accessible from the generated codecs at package '" + codecPackage + "'");
                ok = false;
            }
            if (ok) {
                valid.add(fqn);
            }
        }
        return valid;
    }

    private static TypeSpec buildCodec(ValidatedGeneratorElement element) {
        TypeMirror type = element.element().asType();
        // box() returns a reference type unchanged, so no primitive branch is needed here.
        TypeName payloadTypeName = TypeName.get(element.payloadType()).box();
        TypeName elementTypeName = TypeName.get(type);

        String codecClassName = element.name().flatName() + "Codec";
        TypeName innerCodecTypeName = ParameterizedTypeName.get(CODEC, payloadTypeName);

        FieldSpec innerCodecField = FieldSpec.builder(innerCodecTypeName, "innerCodec", Modifier.PRIVATE, Modifier.FINAL)
                .build();

        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ParameterSpec.builder(innerCodecTypeName, "innerCodec").build())
                .addStatement("this.innerCodec = innerCodec")
                .build();

        // Codec follows the Mongo driver convention: invoked only on non-null BSON tokens;
        // standard call paths (PojoCodec, IterableCodec, ...) filter nulls upstream.
        // See the class-level Javadoc for details.
        MethodSpec encode = MethodSpec.methodBuilder("encode")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.VOID)
                .addParameter(BSON_WRITER, "writer")
                .addParameter(elementTypeName, "value")
                .addParameter(ENCODER_CONTEXT, "encoderContext")
                .addStatement("innerCodec.encode(writer, $L, encoderContext)", code(element.java().read("value")))
                .build();

        MethodSpec decode = MethodSpec.methodBuilder("decode")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .returns(elementTypeName)
                .addParameter(BSON_READER, "reader")
                .addParameter(DECODER_CONTEXT, "decoderContext")
                .addStatement("return $L", code(element.java().create("innerCodec.decode(reader, decoderContext)")))
                .build();

        MethodSpec getEncoderClass = MethodSpec.methodBuilder("getEncoderClass")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(JAVA_LANG_CLASS, elementTypeName))
                .addStatement("return $T.class", elementTypeName)
                .build();

        return TypeSpec.classBuilder(codecClassName)
                .addSuperinterface(ParameterizedTypeName.get(CODEC, elementTypeName))
                .addField(innerCodecField)
                .addMethod(constructor)
                .addMethod(encode)
                .addMethod(decode)
                .addMethod(getEncoderClass)
                .build();
    }

    private static TypeSpec buildCodecsUtility(List<ValidatedGeneratorElement> elements,
                                               List<TypeSpec> codecSpecs,
                                               List<String> userCodecFqns,
                                               boolean hasMongoDriverCore) {
        TypeName codecWildcard = ParameterizedTypeName.get(CODEC, WildcardTypeName.subtypeOf(Object.class));
        ArrayTypeName codecArray = ArrayTypeName.of(codecWildcard);

        FieldSpec userCodecsField = FieldSpec.builder(codecArray, "userCodecs", Modifier.PRIVATE, Modifier.FINAL).build();

        MethodSpec constructor = buildConstructor(elements, userCodecFqns);

        MethodSpec getMethod = buildGetMethod(elements, !userCodecFqns.isEmpty());

        var builder = TypeSpec.classBuilder(CODECS_CLASS_NAME)
                .addJavadoc("""
                        A {@link $T} with one native MongoDB {@code Codec} per generated domain-primitive.

                        <p>{@code get} intentionally does not cache generated codecs: for generated types it returns a fresh codec on every call.
                        The MongoDB registry ({@code ProvidersCodecRegistry}) already memoizes the result per
                        {@code (Class, typeArguments)}, so it is invoked at most once per type per registry, not per
                        {@code encode}/{@code decode}. Caching here would be redundant, and since each generated codec is bound
                        to the {@code registry} it was built from, could return a codec wired to the wrong registry.
                        <p>User-supplied codecs (via {@code lazyval.mongodb.codecs}) are instantiated once and returned as-is.
                        """, CODEC_PROVIDER)
                .addAnnotation(GeneratedStamp.forGenerator(MongoCodecGenerator.class))
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(CODEC_PROVIDER)
                .addField(userCodecsField)
                .addMethod(constructor)
                .addMethod(getMethod);

        if (hasMongoDriverCore) {
            MethodSpec asRegistry = MethodSpec.methodBuilder("asRegistry")
                    .addJavadoc("""
                                    Convenience method returning a {@link $T} that combines the default Mongo
                                    registry with this provider. Use it for one-line setup outside of CDI:
                                    <pre>{@code
                                    MongoClientSettings settings = MongoClientSettings.builder()
                                        .codecRegistry(LazyvalMongoCodecs.asRegistry())
                                        .build();
                                    }</pre>

                                    @return a {@code CodecRegistry} with the default registry and the generated codecs
                                    """,
                            CODEC_REGISTRY)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(CODEC_REGISTRY)
                    .addStatement("return $T.fromRegistries($T.getDefaultCodecRegistry(), $T.fromProviders(new $L()))",
                            CODEC_REGISTRIES, MONGO_CLIENT_SETTINGS, CODEC_REGISTRIES, CODECS_CLASS_NAME)
                    .build();
            builder.addMethod(asRegistry);
        }

        codecSpecs.forEach(spec -> builder.addType(spec.toBuilder().addModifiers(Modifier.STATIC).build()));

        return builder.build();
    }

    private static MethodSpec buildConstructor(List<ValidatedGeneratorElement> elements,
                                               List<String> userCodecFqns) {
        MethodSpec.Builder ctor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);

        if (userCodecFqns.isEmpty()) {
            ctor.addStatement("this.userCodecs = new $T<?>[0]", CODEC);
            return ctor.build();
        }

        StringBuilder arrayInit = new StringBuilder("this.userCodecs = new $T<?>[] {\n");
        for (int i = 0; i < userCodecFqns.size(); i++) {
            arrayInit.append("    new ").append(userCodecFqns.get(i)).append("()");
            if (i < userCodecFqns.size() - 1) {
                arrayInit.append(",");
            }
            arrayInit.append("\n");
        }
        arrayInit.append("}");
        ctor.addStatement(arrayInit.toString(), CODEC);

        StringBuilder generatedSetInit = new StringBuilder("$T<$T<?>> generatedTypes = $T.of(\n");
        for (int i = 0; i < elements.size(); i++) {
            generatedSetInit.append("    ").append(elements.get(i).name().nestedName()).append(".class");
            if (i < elements.size() - 1) {
                generatedSetInit.append(",");
            }
            generatedSetInit.append("\n");
        }
        generatedSetInit.append(")");
        ClassName setClass = ClassName.get("java.util", "Set");
        ctor.addStatement(generatedSetInit.toString(), setClass, JAVA_LANG_CLASS, setClass);

        ctor.addStatement("$T logger = $T.getLogger($L.class.getName())", SYSTEM_LOGGER, SYSTEM, CODECS_CLASS_NAME);
        ctor.beginControlFlow("for ($T<?> userCodec : this.userCodecs)", CODEC);
        ctor.beginControlFlow("if (generatedTypes.contains(userCodec.getEncoderClass()))");
        ctor.addStatement(
                "logger.log($T.INFO, () -> \"User-supplied codec \" + userCodec.getClass().getName() "
                        + "+ \" overrides the generated codec for \" + userCodec.getEncoderClass().getName())",
                SYSTEM_LOGGER_LEVEL);
        ctor.endControlFlow();
        ctor.endControlFlow();

        return ctor.build();
    }

    private static MethodSpec buildGetMethod(List<ValidatedGeneratorElement> elements, boolean hasUserCodecs) {
        TypeVariableName t = TypeVariableName.get("T");

        MethodSpec.Builder method = MethodSpec.methodBuilder("get")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addAnnotation(SUPPRESS_UNCHECKED)
                .addModifiers(Modifier.PUBLIC)
                .addTypeVariable(t)
                .returns(ParameterizedTypeName.get(CODEC, t))
                .addParameter(ParameterizedTypeName.get(JAVA_LANG_CLASS, t), "clazz")
                .addParameter(CODEC_REGISTRY, "registry");

        if (hasUserCodecs) {
            method.addComment("user codecs override generated ones (last-wins)")
                    .beginControlFlow("for ($T<?> userCodec : userCodecs)", CODEC)
                    .beginControlFlow("if (userCodec.getEncoderClass().equals(clazz))")
                    .addStatement("return ($T<T>) userCodec", CODEC)
                    .endControlFlow()
                    .endControlFlow();
        }

        for (ValidatedGeneratorElement element : elements) {
            TypeName elementTypeName = TypeName.get(element.element().asType());
            TypeName payloadTypeName = TypeName.get(element.payloadType()).box();
            String codecClassName = element.name().flatName() + "Codec";

            method.beginControlFlow("if (clazz == $T.class)", elementTypeName)
                    .addStatement("return ($T<T>) new $L(registry.get($T.class))", CODEC, codecClassName, payloadTypeName)
                    .endControlFlow();
        }

        method.addStatement("return null");
        return method.build();
    }

    private static TypeSpec buildQuarkusRegistrar(String codecPackage) {
        ClassName applicationScoped = ClassName.get("jakarta.enterprise.context", "ApplicationScoped");
        ClassName unremovable = ClassName.get("io.quarkus.arc", "Unremovable");
        ClassName codecsClass = ClassName.get(codecPackage, CODECS_CLASS_NAME);
        TypeVariableName t = TypeVariableName.get("T");

        FieldSpec delegateField = FieldSpec.builder(codecsClass, "delegate", Modifier.PRIVATE, Modifier.FINAL).build();

        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement("this.delegate = new $T()", codecsClass)
                .build();

        MethodSpec getMethod = MethodSpec.methodBuilder("get")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PUBLIC)
                .addTypeVariable(t)
                .returns(ParameterizedTypeName.get(CODEC, t))
                .addParameter(ParameterizedTypeName.get(JAVA_LANG_CLASS, t), "clazz")
                .addParameter(CODEC_REGISTRY, "registry")
                .addStatement("return delegate.get(clazz, registry)")
                .build();

        return TypeSpec.classBuilder(REGISTRAR_CLASS_NAME)
                .addJavadoc("""
                        A Quarkus {@link $T} CDI bean that delegates to {@link $T}; Quarkus auto-discovers it and
                        chains it into the default Mongo registry.

                        <p>Like the delegate, {@code get} does not cache generated codecs (for generated types it returns a fresh codec per call).
                        The driver's registry ({@code ProvidersCodecRegistry}) memoizes the result per
                        {@code (Class, typeArguments)}, so it is consulted at most once per type per registry, not
                        per {@code encode}/{@code decode}. User-supplied codecs (via {@code lazyval.mongodb.codecs}) are instantiated once and returned as-is.
                        """, CODEC_PROVIDER, codecsClass)
                .addAnnotation(GeneratedStamp.forGenerator(MongoCodecGenerator.class))
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(applicationScoped)
                .addAnnotation(unremovable)
                .addSuperinterface(CODEC_PROVIDER)
                .addField(delegateField)
                .addMethod(constructor)
                .addMethod(getMethod)
                .build();
    }
}
