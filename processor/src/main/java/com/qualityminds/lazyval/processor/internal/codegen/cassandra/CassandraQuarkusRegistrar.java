package com.qualityminds.lazyval.processor.internal.codegen.cassandra;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.qualityminds.lazyval.processor.internal.codegen.GeneratedStamp;

import javax.lang.model.element.Modifier;
import java.util.List;

/**
 * Builds the {@code LazyvalCassandraCodecRegistrar} CDI bean that registers the generated
 * {@code MappingCodec}s with the Quarkus-managed {@code QuarkusCqlSession} at startup.
 * <p>
 * The registrar acts as an {@code @Alternative} for the default {@code CassandraClientProducer},
 * intercepts the session-producing {@code CompletionStage}, and registers each codec on its
 * {@code MutableCodecRegistry} before the session is exposed to application beans.
 */
final class CassandraQuarkusRegistrar {

    private static final String REGISTRAR_CLASS_NAME = "LazyvalCassandraCodecRegistrar";

    private static final ClassName QUARKUS_CQL_SESSION = ClassName.get("com.datastax.oss.quarkus.runtime.api.session", "QuarkusCqlSession");
    private static final ClassName MUTABLE_CODEC_REGISTRY = ClassName.get("com.datastax.oss.driver.api.core.type.codec.registry", "MutableCodecRegistry");
    private static final ClassName CASSANDRA_CLIENT_CONFIG = ClassName.get("com.datastax.oss.quarkus.runtime.api.config", "CassandraClientConfig");
    private static final ClassName CASSANDRA_CLIENT_PRODUCER = ClassName.get("com.datastax.oss.quarkus.runtime.internal.quarkus", "CassandraClientProducer");
    private static final ClassName COMPLETION_STAGE = ClassName.get("java.util.concurrent", "CompletionStage");
    private static final ClassName EVENT_LOOP_GROUP = ClassName.get("io.netty.channel", "EventLoopGroup");
    private static final ClassName MAIN_EVENT_LOOP_GROUP = ClassName.get("io.quarkus.netty", "MainEventLoopGroup");
    private static final ClassName APPLICATION_SCOPED = ClassName.get("jakarta.enterprise.context", "ApplicationScoped");
    private static final ClassName PRODUCES = ClassName.get("jakarta.enterprise.inject", "Produces");
    private static final ClassName ALTERNATIVE = ClassName.get("jakarta.enterprise.inject", "Alternative");
    private static final ClassName UNREMOVABLE = ClassName.get("io.quarkus.arc", "Unremovable");
    private static final ClassName INJECT = ClassName.get("jakarta.inject", "Inject");
    private static final ClassName PRIORITY = ClassName.get("jakarta.annotation", "Priority");

    private CassandraQuarkusRegistrar() {
    }

    static TypeSpec build(List<String> codecClassNames) {
        return TypeSpec.classBuilder(REGISTRAR_CLASS_NAME)
                .addAnnotation(GeneratedStamp.forGenerator(CassandraCodecGenerator.class))
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(APPLICATION_SCOPED)
                .addAnnotation(ALTERNATIVE)
                .addAnnotation(AnnotationSpec.builder(PRIORITY)
                        .addMember("value", "$L", 1)
                        .build())
                .addField(FieldSpec.builder(CASSANDRA_CLIENT_PRODUCER, "delegate", Modifier.PRIVATE, Modifier.FINAL).build())
                .addMethod(buildConstructor())
                .addMethod(buildProduceMethod(codecClassNames))
                .build();
    }

    private static MethodSpec buildConstructor() {
        return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(INJECT)
                .addParameter(CASSANDRA_CLIENT_PRODUCER, "delegate")
                .addStatement("this.delegate = delegate")
                .build();
    }

    private static MethodSpec buildProduceMethod(List<String> codecClassNames) {
        TypeName sessionStageType = ParameterizedTypeName.get(COMPLETION_STAGE, QUARKUS_CQL_SESSION);
        String codecRegistrations = codecClassNames.stream()
                .map(name -> "    registry.register(new LazyvalCassandraCodecs." + name + "());\n")
                .reduce("", String::concat);

        return MethodSpec.methodBuilder("produceCodecAwareSessionStage")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(PRODUCES)
                .addAnnotation(APPLICATION_SCOPED)
                .addAnnotation(UNREMOVABLE)
                .returns(sessionStageType)
                .addParameter(ParameterSpec.builder(CASSANDRA_CLIENT_CONFIG, "config").build())
                .addParameter(ParameterSpec.builder(EVENT_LOOP_GROUP, "mainEventLoop")
                        .addAnnotation(MAIN_EVENT_LOOP_GROUP)
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
                        MUTABLE_CODEC_REGISTRY)
                .addCode(codecRegistrations)
                .addCode("""
                            return session;
                        });
                        """)
                .build();
    }
}
