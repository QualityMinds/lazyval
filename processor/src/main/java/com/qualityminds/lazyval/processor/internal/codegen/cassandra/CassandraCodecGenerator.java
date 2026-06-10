package com.qualityminds.lazyval.processor.internal.codegen.cassandra;

import com.palantir.javapoet.*;
import com.qualityminds.lazyval.collections.NonEmptySet;
import com.qualityminds.lazyval.processor.internal.codegen.GeneratedStamp;
import com.qualityminds.lazyval.processor.spi.Generator;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.stream.Stream;

/**
 * Generates a DataStax {@code MappingCodec} for each domain-primitive, grouped into a
 * {@code LazyvalCassandraCodecs} utility class.
 *
 * <h3>Null invariants</h3>
 * The DataStax driver decodes a CQL {@code NULL} column to {@code null} and passes it
 * directly to {@code innerToOuter}. Both {@code innerToOuter} and {@code outerToInner}
 * guard against {@code null} explicitly and propagate it as-is without invoking the factory.
 * Java's type system does not enforce nullability on the generated methods; the
 * {@code GenericType} constructor argument always carries the non-nullable Java class,
 * regardless of whether the factory method can return {@code null}.
 */
// must only be public for ServiceLoader, but it is not part of the API
public class CassandraCodecGenerator implements Generator {

    private static final String GENERATOR_ID = "cassandra";
    private static final String OPTION_GENERATED_PACKAGE = "lazyval.cassandra.package";
    private static final String OPTION_QUARKUS_REGISTER = "lazyval.cassandra.quarkus.register";

    private static final ClassName MAPPING_CODEC = ClassName.get("com.datastax.oss.driver.api.core.type.codec", "MappingCodec");
    private static final ClassName TYPE_CODECS = ClassName.get("com.datastax.oss.driver.api.core.type.codec", "TypeCodecs");
    private static final ClassName GENERIC_TYPE = ClassName.get("com.datastax.oss.driver.api.core.type.reflect", "GenericType");
    private static final ClassName TYPE_CODEC = ClassName.get("com.datastax.oss.driver.api.core.type.codec", "TypeCodec");
    private static final AnnotationSpec OVERRIDE_ANNOTATION = AnnotationSpec.builder(
            ClassName.get("java.lang", "Override")).build();

    /**
     * Maps Java type names to their corresponding {@code TypeCodecs} constant names.
     * <p>
     * This mapping is necessary because the generated {@code MappingCodec} subclasses require a
     * compile-time reference to a specific {@code TypeCodecs} constant (e.g., {@code TypeCodecs.TEXT})
     * in their constructor call. Since the DataStax driver uses CQL type names for its constants
     * rather than Java type names, and there is no {@code TypeCodecs.forJavaType(Class)} lookup method,
     * we need an explicit mapping. Many CQL type names differ from their Java counterparts
     * (e.g., {@code long} &rarr; {@code BIGINT}, {@code Instant} &rarr; {@code TIMESTAMP},
     * {@code BigInteger} &rarr; {@code VARINT}).
     */
    private static final Map<String, String> TYPE_CODEC_MAP = Map.ofEntries(
            Map.entry("java.lang.String", "TEXT"),
            Map.entry("int", "INT"),
            Map.entry("java.lang.Integer", "INT"),
            Map.entry("long", "BIGINT"),
            Map.entry("java.lang.Long", "BIGINT"),
            Map.entry("double", "DOUBLE"),
            Map.entry("java.lang.Double", "DOUBLE"),
            Map.entry("float", "FLOAT"),
            Map.entry("java.lang.Float", "FLOAT"),
            Map.entry("boolean", "BOOLEAN"),
            Map.entry("java.lang.Boolean", "BOOLEAN"),
            Map.entry("short", "SMALLINT"),
            Map.entry("java.lang.Short", "SMALLINT"),
            Map.entry("byte", "TINYINT"),
            Map.entry("java.lang.Byte", "TINYINT"),
            Map.entry("java.time.LocalDate", "DATE"),
            Map.entry("java.time.LocalTime", "TIME"),
            Map.entry("java.time.Instant", "TIMESTAMP"),
            Map.entry("java.util.UUID", "UUID"),
            Map.entry("java.math.BigDecimal", "DECIMAL"),
            Map.entry("java.math.BigInteger", "VARINT"),
            Map.entry("java.net.InetAddress", "INET"),
            Map.entry("java.nio.ByteBuffer", "BLOB")
    );

    @Override
    public String generatorId() {
        return GENERATOR_ID;
    }

    @Override
    public Collection<String> requiredClasspath() {
        return List.of("com.datastax.oss.driver.api.core.type.codec.MappingCodec");
    }

    @Override
    public Set<String> supportedOptions() {
        return Set.of(OPTION_GENERATED_PACKAGE, OPTION_QUARKUS_REGISTER);
    }

    @Override
    public Stream<GeneratorResult> generate(NonEmptySet<ValidatedGeneratorElement> elements, Context context) {
        final String codecPackage = context.generatorPackage(OPTION_GENERATED_PACKAGE, "boundary.persistence.cassandra");

        List<GeneratorResult> results = new ArrayList<>();
        List<TypeSpec> codecSpecs = new ArrayList<>();

        for (ValidatedGeneratorElement element : elements) {
            String typeCodecConstant = resolveTypeCodecConstant(element);
            codecSpecs.add(buildMappingCodec(element, typeCodecConstant));
        }

        if (!codecSpecs.isEmpty()) {
            TypeSpec utilitySpec = buildCodecsUtility(codecSpecs);
            JavaFile utilityFile = JavaFile.builder(codecPackage, utilitySpec).build();
            results.add(new GeneratorResult.Java(
                    new GeneratorResult.Metadata(utilityFile.packageName(), utilityFile.typeSpec().name()),
                    utilityFile.toString()));

            boolean isQuarkus = context.isOnClasspath("com.datastax.oss.quarkus.runtime.api.session.QuarkusCqlSession");
            boolean quarkusRegister = context.getSetting(OPTION_QUARKUS_REGISTER)
                    .map(v -> !"false".equalsIgnoreCase(v))
                    .orElse(true);

            if (isQuarkus && quarkusRegister) {
                List<String> codecClassNames = codecSpecs.stream().map(TypeSpec::name).toList();
                TypeSpec registrarSpec = buildQuarkusRegistrar(codecClassNames);
                JavaFile registrarFile = JavaFile.builder(codecPackage, registrarSpec).build();
                results.add(new GeneratorResult.Java(
                        new GeneratorResult.Metadata(registrarFile.packageName(), registrarFile.typeSpec().name()),
                        registrarFile.toString()));
            }
        }

        return results.stream();
    }

    private static String resolveTypeCodecConstant(ValidatedGeneratorElement element) {
        TypeMirror wrappedMirror = element.wrappedType().typeMirror();
        String typeName = wrappedMirror.toString();
        return TYPE_CODEC_MAP.get(typeName);
    }

    private static TypeSpec buildMappingCodec(ValidatedGeneratorElement element, String typeCodecConstant) {
        TypeMirror type = element.element().asType();
        var wrappedType = element.wrappedType();

        TypeName wrappedTypeName;
        if (wrappedType.isPrimitive()) {
            wrappedTypeName = TypeName.get(wrappedType.typeMirror()).box();
        } else {
            wrappedTypeName = TypeName.get(wrappedType.typeMirror());
        }
        TypeName elementTypeName = TypeName.get(type);

        String codecClassName = element.typeName().name() + "Codec";

        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement("super($T.$L, $T.of($T.class))", TYPE_CODECS, typeCodecConstant, GENERIC_TYPE, elementTypeName)
                .build();

        MethodSpec innerToOuter = MethodSpec.methodBuilder("innerToOuter")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PROTECTED)
                .returns(elementTypeName)
                .addParameter(ParameterSpec.builder(wrappedTypeName, "value").build())
                .beginControlFlow("if (value == null)")
                .addStatement("return null")
                .endControlFlow()
                .addStatement("return $L", element.objectCreation("value"))
                .build();

        MethodSpec outerToInner = MethodSpec.methodBuilder("outerToInner")
                .addAnnotation(OVERRIDE_ANNOTATION)
                .addModifiers(Modifier.PROTECTED)
                .returns(wrappedTypeName)
                .addParameter(ParameterSpec.builder(elementTypeName, "value").build())
                .beginControlFlow("if (value == null)")
                .addStatement("return null")
                .endControlFlow()
                .addStatement("return value.$L", element.accessor())
                .build();

        return TypeSpec.classBuilder(codecClassName)
                .superclass(ParameterizedTypeName.get(MAPPING_CODEC, wrappedTypeName, elementTypeName))
                .addMethod(constructor)
                .addMethod(innerToOuter)
                .addMethod(outerToInner)
                .build();
    }

    private static TypeSpec buildCodecsUtility(List<TypeSpec> codecSpecs) {
        List<String> codecClassNames = codecSpecs.stream().map(TypeSpec::name).toList();

        MethodSpec.Builder allMethod = MethodSpec.methodBuilder("all")
                .addJavadoc("""
                                Returns an array of all generated {@link $T}s for lazyval wrapper types.
                                <p>
                                Use this method to register all codecs at once, e.g.:
                                <pre>{@code
                                CqlSession session = CqlSession.builder()
                                    .addTypeCodecs(LazyvalCassandraCodecs.all())
                                    .build();
                                }</pre>
                                
                                @return an array containing one codec instance per generated wrapper type
                                """,
                        TYPE_CODEC)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ArrayTypeName.of(ParameterizedTypeName.get(TYPE_CODEC, WildcardTypeName.subtypeOf(Object.class))));

        StringBuilder arrayInit = new StringBuilder("return new $T[] {\n");
        for (int i = 0; i < codecClassNames.size(); i++) {
            arrayInit.append("    new ").append(codecClassNames.get(i)).append("()");
            if (i < codecClassNames.size() - 1) {
                arrayInit.append(",");
            }
            arrayInit.append("\n");
        }
        arrayInit.append("}");
        allMethod.addStatement(arrayInit.toString(), TYPE_CODEC);

        var builder = TypeSpec.classBuilder("LazyvalCassandraCodecs")
                .addAnnotation(GeneratedStamp.forGenerator(CassandraCodecGenerator.class))
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PRIVATE)
                        .build())
                .addMethod(allMethod.build());

        codecSpecs.forEach(spec -> builder.addType(spec.toBuilder().addModifiers(Modifier.STATIC).build()));

        return builder.build();
    }

    private static TypeSpec buildQuarkusRegistrar(List<String> codecClassNames) {
        ClassName quarkusCqlSession = ClassName.get("com.datastax.oss.quarkus.runtime.api.session", "QuarkusCqlSession");
        ClassName cassandraClientConfig = ClassName.get("com.datastax.oss.quarkus.runtime.api.config", "CassandraClientConfig");
        ClassName cassandraClientProducer = ClassName.get("com.datastax.oss.quarkus.runtime.internal.quarkus", "CassandraClientProducer");
        ClassName completionStage = ClassName.get("java.util.concurrent", "CompletionStage");
        TypeName sessionStageType = ParameterizedTypeName.get(completionStage, quarkusCqlSession);

        // Generate a method that observes the produced CompletionStage and
        // registers codecs right after session creation but before anyone uses it.
        // This doesn't work — we need to intercept BEFORE buildAsync().

        // The correct approach: generate an @Alternative producer that replaces
        // the original CompletionStage<QuarkusCqlSession> producer, adding codecs
        // to the builder before buildAsync() is called.

        // Actually the simplest approach: since MutableCodecRegistry supports
        // runtime registration, and the REAL issue is that the mapper's thenApply
        // runs when the stage completes, we need to register codecs in a
        // thenApply that runs BEFORE the mapper's thenApply.
        // We can do this by producing the CompletionStage ourselves as @Alternative.

        // But actually the simplest: wrap the original stage with codec registration.

        MethodSpec produceMethod = MethodSpec.methodBuilder("produceCodecAwareSessionStage")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("jakarta.enterprise.inject", "Produces"))
                .addAnnotation(ClassName.get("jakarta.enterprise.context", "ApplicationScoped"))
                .addAnnotation(ClassName.get("io.quarkus.arc", "Unremovable"))
                .returns(sessionStageType)
                .addParameter(
                        ParameterSpec.builder(cassandraClientConfig, "config").build())
                .addParameter(
                        ParameterSpec.builder(
                                        ClassName.get("io.netty.channel", "EventLoopGroup"), "mainEventLoop")
                                .addAnnotation(ClassName.get("io.quarkus.netty", "MainEventLoopGroup"))
                                .build())
                .addStatement("$T stage = delegate.produceQuarkusCqlSessionStage(config, mainEventLoop)", sessionStageType)
                .addCode(
                        """
                                return stage.thenApply(session -> {
                                    var codecRegistry = session.getContext().getCodecRegistry();
                                    if (!(codecRegistry instanceof $T registry)) {
                                        throw new IllegalStateException(
                                            "CodecRegistry does not support runtime registration. Expected MutableCodecRegistry but got: " + codecRegistry.getClass().getName());
                                    }
                                """,
                        ClassName.get("com.datastax.oss.driver.api.core.type.codec.registry", "MutableCodecRegistry"))
                .addCode(codecClassNames.stream()
                        .map(name -> "    registry.register(new LazyvalCassandraCodecs." + name + "());\n")
                        .reduce("", String::concat))
                .addCode("""
                            return session;
                        });
                        """)
                .build();

        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("jakarta.inject", "Inject"))
                .addParameter(cassandraClientProducer, "delegate")
                .addStatement("this.delegate = delegate")
                .build();

        return TypeSpec.classBuilder("LazyvalCassandraCodecRegistrar")
                .addAnnotation(GeneratedStamp.forGenerator(CassandraCodecGenerator.class))
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("jakarta.enterprise.context", "ApplicationScoped"))
                .addAnnotation(ClassName.get("jakarta.enterprise.inject", "Alternative"))
                .addAnnotation(AnnotationSpec.builder(ClassName.get("jakarta.annotation", "Priority"))
                        .addMember("value", "$L", 1)
                        .build())
                .addField(FieldSpec.builder(cassandraClientProducer, "delegate", Modifier.PRIVATE, Modifier.FINAL).build())
                .addMethod(constructor)
                .addMethod(produceMethod)
                .build();
    }
}
