package com.qualityminds.lazyval.ksp.internal.codegen

import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.internal.codegen.GeneratedStamp.addGeneratedAnnotation
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.StockGeneratorIds
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import java.util.stream.Stream

class JsonbGenerator : Generator {

    companion object {
        private const val OPTION_GENERATED_PACKAGE = "lazyval.jsonb.package"
        private const val OPTION_REGISTER = "lazyval.jsonb.register"

        private val JSONB_ADAPTER = ClassName("jakarta.json.bind.adapter", "JsonbAdapter")
        private val JSONB_CONFIG = ClassName("jakarta.json.bind", "JsonbConfig")
        private val JSONB = ClassName("jakarta.json.bind", "Jsonb")
        private val JSONB_BUILDER = ClassName("jakarta.json.bind", "JsonbBuilder")
        private val CONTEXT_RESOLVER = ClassName("jakarta.ws.rs.ext", "ContextResolver")
        private val PROVIDER_ANNOTATION = ClassName("jakarta.ws.rs.ext", "Provider")
        private val QUARKUS_CUSTOMIZER = ClassName("io.quarkus.jsonb", "JsonbConfigCustomizer")
        private val SINGLETON_ANNOTATION = ClassName("jakarta.inject", "Singleton")

        private const val CONTEXT_RESOLVER_FQCN = "jakarta.ws.rs.ext.ContextResolver"
        private const val QUARKUS_CUSTOMIZER_FQCN = "io.quarkus.jsonb.JsonbConfigCustomizer"
    }

    override fun generatorId(): String = StockGeneratorIds.JSONB

    override fun requiredClasspath(): Set<String> = setOf("jakarta.json.bind.adapter.JsonbAdapter")

    override fun supportedOptions(): Set<String> = setOf(OPTION_GENERATED_PACKAGE, OPTION_REGISTER)

    private data class NamedAdapter(val name: String, val typeSpec: TypeSpec)

    override fun generate(
        validatedElements: NonEmptySet<ValidatedKspGeneratorElement>,
        context: Generator.Context
    ): Stream<GeneratorResult> {
        val adapters = validatedElements.map { generateAdapter(context, it) }
        val isQuarkus = context.isOnClasspath(QUARKUS_CUSTOMIZER_FQCN)
        val providerSpec = generateProvider(adapters, isQuarkus)

        val packageName = context.generatorPackage(OPTION_GENERATED_PACKAGE, null)

        val fileSpec = FileSpec.builder(packageName, providerSpec.name!!)
            .addType(providerSpec)
            .build()
        val fileMetadata = GeneratorResult.Metadata(fileSpec.packageName, fileSpec.name)

        val results = Stream.builder<GeneratorResult>()
        results.add(GeneratorResult.Kotlin(fileMetadata, fileSpec.toString()))

        // On Quarkus the JsonbConfigCustomizer above is the idiomatic registration path;
        // a JAX-RS ContextResolver would double-register the same adapters via REST-easy.
        val register = context.getSetting(OPTION_REGISTER)?.toBoolean() ?: true
        if (register && !isQuarkus && context.isOnClasspath(CONTEXT_RESOLVER_FQCN)) {
            val resolverSpec = generateContextResolver(context, fileMetadata)
            val resolverFile = FileSpec.builder(packageName, resolverSpec.name!!)
                .addType(resolverSpec)
                .build()
            val resolverMetadata = GeneratorResult.Metadata(resolverFile.packageName, resolverFile.name)
            results.add(GeneratorResult.Kotlin(resolverMetadata, resolverFile.toString()))
        }

        return results.build()
    }

    private fun generateAdapter(context: Generator.Context, element: ValidatedKspGeneratorElement): NamedAdapter {
        val elementClassName = element.element.toClassName()
        val wrappedType = element.wrappedProperty
        val adapterName = "${element.typeName.name}Adapter"
        val wrappedTypeName = wrappedType.type.toTypeName()

        val factoryMethod = element.factoryMethod
        val objectCreation = if (factoryMethod != null) {
            "${element.typeName}.${factoryMethod.simpleName.asString()}(value)"
        } else {
            "${element.typeName}(value)"
        }

        val adaptToJson = FunSpec.builder("adaptToJson")
            .addModifiers(KModifier.OVERRIDE)
            .returns(wrappedTypeName)
            .addParameter("obj", elementClassName)
            .addStatement("return obj.${element.kotlinAccessor}")
            .build()

        val adaptFromJson = FunSpec.builder("adaptFromJson")
            .addModifiers(KModifier.OVERRIDE)
            .returns(elementClassName.copy(nullable = true))
            .addParameter("value", wrappedTypeName)
            .addStatement("return $objectCreation")
            .build()

        return NamedAdapter(
            adapterName,
            TypeSpec.classBuilder(adapterName)
                .addGeneratedAnnotation(JsonbGenerator::class, context)
                .addSuperinterface(
                    JSONB_ADAPTER.parameterizedBy(elementClassName, wrappedTypeName)
                )
                .addFunction(adaptToJson)
                .addFunction(adaptFromJson)
                .build()
        )
    }

    private fun generateContextResolver(context: Generator.Context, adaptersMetadata: GeneratorResult.Metadata): TypeSpec {
        val adaptersType = ClassName(adaptersMetadata.packageName, adaptersMetadata.className)

        // Jsonb instances are expensive to create and thread-safe per the JSON-B spec, so the
        // resolver caches a single instance instead of rebuilding it on every JAX-RS lookup.
        val jsonbProperty = PropertySpec.builder("jsonb", JSONB)
            .addModifiers(KModifier.PRIVATE)
            .initializer("%T.create(%T.config())", JSONB_BUILDER, adaptersType)
            .build()

        val getContextFun = FunSpec.builder("getContext")
            .addModifiers(KModifier.OVERRIDE)
            .returns(JSONB)
            .addParameter("type", Class::class.asClassName().parameterizedBy(STAR))
            .addStatement("return jsonb")
            .build()

        return TypeSpec.classBuilder("LazyvalJsonbContextResolver")
            .addGeneratedAnnotation(JsonbGenerator::class, context)
            .addAnnotation(PROVIDER_ANNOTATION)
            .addSuperinterface(CONTEXT_RESOLVER.parameterizedBy(JSONB))
            .addProperty(jsonbProperty)
            .addFunction(getContextFun)
            .build()
    }

    private fun generateProvider(adapters: List<NamedAdapter>, isQuarkus: Boolean): TypeSpec {
        val adapterArrayType = ARRAY.parameterizedBy(JSONB_ADAPTER.parameterizedBy(STAR, STAR))

        val adaptersBody = CodeBlock.builder()
            .add("return arrayOf(\n")
            .indent()
        for (i in adapters.indices) {
            adaptersBody.add("%T()", ClassName("", adapters[i].name))
            if (i < adapters.size - 1) {
                adaptersBody.add(",")
            }
            adaptersBody.add("\n")
        }
        adaptersBody.unindent().add(")")

        val adaptersFun = FunSpec.builder("adapters")
            .addAnnotation(JvmStatic::class)
            .returns(adapterArrayType)
            .addCode(adaptersBody.build())
            .build()

        val configFun = FunSpec.builder("config")
            .addAnnotation(JvmStatic::class)
            .returns(JSONB_CONFIG)
            .addStatement("return %T().withAdapters(*adapters())", JSONB_CONFIG)
            .build()

        val companionBuilder = TypeSpec.companionObjectBuilder()
            .addFunction(adaptersFun)
            .addFunction(configFun)
            .build()

        val providerBuilder = TypeSpec.classBuilder("LazyvalJsonbAdapters")
            .addType(companionBuilder)

        if (isQuarkus) {
            providerBuilder
                .addAnnotation(SINGLETON_ANNOTATION)
                .addSuperinterface(QUARKUS_CUSTOMIZER)
                .addFunction(buildQuarkusCustomizer())
        }

        adapters.forEach { a ->
            providerBuilder.addType(a.typeSpec)
        }

        return providerBuilder.build()
    }

    private fun buildQuarkusCustomizer(): FunSpec =
        FunSpec.builder("customize")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("jsonbConfig", JSONB_CONFIG)
            .addStatement("jsonbConfig.withAdapters(*adapters())")
            .build()
}
