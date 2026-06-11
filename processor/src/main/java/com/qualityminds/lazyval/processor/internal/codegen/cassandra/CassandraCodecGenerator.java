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
    private static final String OPTION_USER_CODECS = "lazyval.cassandra.codecs";

    private static final String TYPE_CODEC_FQN = "com.datastax.oss.driver.api.core.type.codec.TypeCodec";

    private static final ClassName MAPPING_CODEC = ClassName.get("com.datastax.oss.driver.api.core.type.codec", "MappingCodec");
    private static final ClassName TYPE_CODECS = ClassName.get("com.datastax.oss.driver.api.core.type.codec", "TypeCodecs");
    private static final ClassName GENERIC_TYPE = ClassName.get("com.datastax.oss.driver.api.core.type.reflect", "GenericType");
    private static final ClassName TYPE_CODEC = ClassName.get("com.datastax.oss.driver.api.core.type.codec", "TypeCodec");
    private static final ClassName SYSTEM = ClassName.get("java.lang", "System");
    private static final ClassName SYSTEM_LOGGER = ClassName.get("java.lang", "System", "Logger");
    private static final ClassName SYSTEM_LOGGER_LEVEL = ClassName.get("java.lang", "System", "Logger", "Level");
    private static final ClassName JAVA_LANG_CLASS = ClassName.get("java.lang", "Class");
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
        return Set.of(OPTION_GENERATED_PACKAGE, OPTION_QUARKUS_REGISTER, OPTION_USER_CODECS);
    }

    @Override
    public Stream<GeneratorResult> generate(NonEmptySet<ValidatedGeneratorElement> elements, Context context) {
        final String codecPackage = context.generatorPackage(OPTION_GENERATED_PACKAGE, "boundary.persistence.cassandra");

        List<String> userCodecFqns = validateUserCodecs(context, codecPackage);

        List<ValidatedGeneratorElement> orderedElements = new ArrayList<>();
        List<GeneratorResult> results = new ArrayList<>();
        List<TypeSpec> codecSpecs = new ArrayList<>();

        for (ValidatedGeneratorElement element : elements) {
            String typeCodecConstant = resolveTypeCodecConstant(element);
            orderedElements.add(element);
            codecSpecs.add(buildMappingCodec(element, typeCodecConstant));
        }

        if (!codecSpecs.isEmpty()) {
            TypeSpec utilitySpec = buildCodecsUtility(orderedElements, codecSpecs, userCodecFqns);
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
                TypeSpec registrarSpec = CassandraQuarkusRegistrar.build(codecClassNames, userCodecFqns);
                JavaFile registrarFile = JavaFile.builder(codecPackage, registrarSpec).build();
                results.add(new GeneratorResult.Java(
                        new GeneratorResult.Metadata(registrarFile.packageName(), registrarFile.typeSpec().name()),
                        registrarFile.toString()));
            }
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
            if (!info.isAssignableTo(TYPE_CODEC_FQN)) {
                context.logError(this, OPTION_USER_CODECS + ": class '" + fqn + "' does not implement " + TYPE_CODEC_FQN);
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

    private static TypeSpec buildCodecsUtility(List<ValidatedGeneratorElement> elements,
                                               List<TypeSpec> codecSpecs,
                                               List<String> userCodecFqns) {
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
                                <p>
                                User-supplied codecs configured via {@code lazyval.cassandra.codecs} are
                                appended to the array so they take precedence in DataStax's last-registered-wins
                                resolution.

                                @return an array containing one codec instance per generated wrapper type,
                                followed by one instance of each user-supplied codec
                                """,
                        TYPE_CODEC)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ArrayTypeName.of(ParameterizedTypeName.get(TYPE_CODEC, WildcardTypeName.subtypeOf(Object.class))));

        StringBuilder arrayInit = new StringBuilder("return new $T[] {\n");
        for (int i = 0; i < codecClassNames.size(); i++) {
            arrayInit.append("    new ").append(codecClassNames.get(i)).append("()");
            if (i < codecClassNames.size() - 1 || !userCodecFqns.isEmpty()) {
                arrayInit.append(",");
            }
            arrayInit.append("\n");
        }
        for (int i = 0; i < userCodecFqns.size(); i++) {
            arrayInit.append("    new ").append(userCodecFqns.get(i)).append("()");
            if (i < userCodecFqns.size() - 1) {
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

        if (!userCodecFqns.isEmpty()) {
            builder.addStaticBlock(buildOverrideDetectionBlock(elements, userCodecFqns));
        }

        codecSpecs.forEach(spec -> builder.addType(spec.toBuilder().addModifiers(Modifier.STATIC).build()));

        return builder.build();
    }

    private static CodeBlock buildOverrideDetectionBlock(List<ValidatedGeneratorElement> elements,
                                                         List<String> userCodecFqns) {
        ClassName setClass = ClassName.get("java.util", "Set");

        StringBuilder generatedSetInit = new StringBuilder("$T<$T<?>> generatedTypes = $T.of(\n");
        for (int i = 0; i < elements.size(); i++) {
            generatedSetInit.append("    ").append(elements.get(i).typeName()).append(".class");
            if (i < elements.size() - 1) {
                generatedSetInit.append(",");
            }
            generatedSetInit.append("\n");
        }
        generatedSetInit.append(")");

        StringBuilder userCodecArrayInit = new StringBuilder("$T<?>[] userCodecs = new $T<?>[] {\n");
        for (int i = 0; i < userCodecFqns.size(); i++) {
            userCodecArrayInit.append("    new ").append(userCodecFqns.get(i)).append("()");
            if (i < userCodecFqns.size() - 1) {
                userCodecArrayInit.append(",");
            }
            userCodecArrayInit.append("\n");
        }
        userCodecArrayInit.append("}");

        return CodeBlock.builder()
                .addStatement("$T logger = $T.getLogger(LazyvalCassandraCodecs.class.getName())", SYSTEM_LOGGER, SYSTEM)
                .addStatement(generatedSetInit.toString(), setClass, JAVA_LANG_CLASS, setClass)
                .addStatement(userCodecArrayInit.toString(), TYPE_CODEC, TYPE_CODEC)
                .beginControlFlow("for ($T<?> userCodec : userCodecs)", TYPE_CODEC)
                .beginControlFlow("if (generatedTypes.contains(userCodec.getJavaType().getRawType()))")
                .addStatement(
                        "logger.log($T.INFO, () -> \"User-supplied codec \" + userCodec.getClass().getName() "
                                + "+ \" overrides the generated codec for \" + userCodec.getJavaType().getRawType().getName())",
                        SYSTEM_LOGGER_LEVEL)
                .endControlFlow()
                .endControlFlow()
                .build();
    }

}
