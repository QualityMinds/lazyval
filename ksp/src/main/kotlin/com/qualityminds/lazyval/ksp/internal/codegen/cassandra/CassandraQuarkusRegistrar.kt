package com.qualityminds.lazyval.ksp.internal.codegen.cassandra

import com.qualityminds.lazyval.ksp.internal.codegen.GeneratedStamp.addGeneratedAnnotation
import com.qualityminds.lazyval.ksp.spi.Generator
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

private val QUARKUS_CQL_SESSION = ClassName("com.datastax.oss.quarkus.runtime.api.session", "QuarkusCqlSession")
private val MUTABLE_CODEC_REGISTRY = ClassName("com.datastax.oss.driver.api.core.type.codec.registry", "MutableCodecRegistry")
private val CASSANDRA_CLIENT_CONFIG = ClassName("com.datastax.oss.quarkus.runtime.api.config", "CassandraClientConfig")
private val CASSANDRA_CLIENT_PRODUCER = ClassName("com.datastax.oss.quarkus.runtime.internal.quarkus", "CassandraClientProducer")
private val COMPLETION_STAGE = ClassName("java.util.concurrent", "CompletionStage")
private val EVENT_LOOP_GROUP = ClassName("io.netty.channel", "EventLoopGroup")
private val MAIN_EVENT_LOOP_GROUP = ClassName("io.quarkus.netty", "MainEventLoopGroup")
private val APPLICATION_SCOPED = ClassName("jakarta.enterprise.context", "ApplicationScoped")
private val PRODUCES = ClassName("jakarta.enterprise.inject", "Produces")
private val ALTERNATIVE = ClassName("jakarta.enterprise.inject", "Alternative")
private val UNREMOVABLE = ClassName("io.quarkus.arc", "Unremovable")
private val INJECT = ClassName("jakarta.inject", "Inject")
private val PRIORITY = ClassName("jakarta.annotation", "Priority")

/**
 * Builds the `LazyvalCassandraCodecRegistrar` CDI bean that registers the generated
 * `MappingCodec`s with the Quarkus-managed `QuarkusCqlSession` at startup.
 *
 * The registrar acts as an `@Alternative` for the default `CassandraClientProducer`, intercepts
 * the session-producing `CompletionStage`, and registers each codec on its `MutableCodecRegistry`
 * before the session is exposed to application beans.
 */
internal object CassandraQuarkusRegistrar {

    private const val REGISTRAR_CLASS_NAME = "LazyvalCassandraCodecRegistrar"

    fun build(context: Generator.Context, codecClassNames: List<String>, userCodecFqns: List<String>): TypeSpec {
        return TypeSpec.classBuilder(REGISTRAR_CLASS_NAME)
            .addGeneratedAnnotation(CassandraCodecGenerator::class, context)
            .addAnnotation(APPLICATION_SCOPED)
            .addAnnotation(ALTERNATIVE)
            .addAnnotation(
                AnnotationSpec.builder(PRIORITY).addMember("value = %L", 1).build()
            )
            .primaryConstructor(buildConstructor())
            .addProperty(
                PropertySpec.builder("delegate", CASSANDRA_CLIENT_PRODUCER)
                    .initializer("delegate")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addFunction(buildProduceFunction(codecClassNames, userCodecFqns))
            .build()
    }

    private fun buildConstructor(): FunSpec =
        FunSpec.constructorBuilder()
            .addAnnotation(INJECT)
            .addParameter("delegate", CASSANDRA_CLIENT_PRODUCER)
            .build()

    private fun buildProduceFunction(codecClassNames: List<String>, userCodecFqns: List<String>): FunSpec {
        val sessionStageType = COMPLETION_STAGE.parameterizedBy(QUARKUS_CQL_SESSION)
        // Generated codecs first, then user-supplied — last-registered wins in DataStax's codec resolution.
        val generatedRegistrations = codecClassNames.map { name ->
            "    registry.register(LazyvalCassandraCodecs.$name())"
        }
        val userRegistrations = userCodecFqns.map { fqn ->
            "    registry.register($fqn())"
        }
        val codecRegistrations = (generatedRegistrations + userRegistrations).joinToString("\n")

        return FunSpec.builder("produceCodecAwareSessionStage")
            .addAnnotation(PRODUCES)
            .addAnnotation(APPLICATION_SCOPED)
            .addAnnotation(UNREMOVABLE)
            .returns(sessionStageType)
            .addParameter("config", CASSANDRA_CLIENT_CONFIG)
            .addParameter(
                ParameterSpec.builder("mainEventLoop", EVENT_LOOP_GROUP)
                    .addAnnotation(MAIN_EVENT_LOOP_GROUP)
                    .build()
            )
            .addStatement("val stage = delegate.produceQuarkusCqlSessionStage(config, mainEventLoop)")
            .addCode(buildRegistrationBlock(codecRegistrations))
            .build()
    }

    private fun buildRegistrationBlock(codecRegistrations: String): CodeBlock =
        CodeBlock.of(
            """
            |return stage.thenApply { session ->
            |    val registry = session.context.codecRegistry as? %T
            |        ?: throw IllegalStateException(
            |            "CodecRegistry does not support runtime registration. Expected MutableCodecRegistry but got: " + session.context.codecRegistry::class.java.name
            |        )
            |%L
            |    session
            |}
            |""".trimMargin(),
            MUTABLE_CODEC_REGISTRY,
            codecRegistrations
        )
}
