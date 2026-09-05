package com.qualityminds.lazyval.ksp.internal.codegen

import com.palantir.javapoet.*
import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.internal.codegen.GeneratedStamp.addGeneratedAnnotation
import com.qualityminds.lazyval.ksp.spi.*
import java.util.stream.Stream
import javax.lang.model.element.Modifier

class MapstructGenerator : Generator {

    companion object {
        private const val OPTION_GENERATED_PACKAGE = "lazyval.mapstruct.package"
    }

    override fun generatorId(): String = StockGeneratorIds.MAPSTRUCT

    override fun requiredClasspath(): Set<String> = setOf("org.mapstruct.Mapper")

    override fun supportedOptions(): Set<String> {
        return setOf(OPTION_GENERATED_PACKAGE)
    }

    override fun generate(
        validatedElements: NonEmptySet<ValidatedKspGeneratorElement>,
        context: Generator.Context
    ): Stream<GeneratorResult> {
        // Build Java interface using JavaPoet
        val interfaceBuilder = TypeSpec.interfaceBuilder("LazyvalMapper")
            .addGeneratedAnnotation(MapstructGenerator::class, context)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(
                AnnotationSpec.builder(ClassName.get("org.mapstruct", "Mapper"))
                    .addMember("unmappedTargetPolicy", $$"$T.ERROR",
                        ClassName.get("org.mapstruct", "ReportingPolicy"))
                    .build()
            )

        validatedElements.forEach { element ->
            interfaceBuilder.addMethod(createJavaMapToPayloadMethod(element))
            interfaceBuilder.addMethod(createJavaMapFromPayloadMethod(element))
        }

        val packageName = context.generatorPackage(OPTION_GENERATED_PACKAGE, null)

        val javaFile = JavaFile.builder(packageName, interfaceBuilder.build())
            .skipJavaLangImports(true)
            .build()

        return Stream.of(GeneratorResult.Java(
            GeneratorResult.Metadata(packageName, "LazyvalMapper"),
            javaFile.toString()))
    }

    private fun createJavaMapToPayloadMethod(element: ValidatedKspGeneratorElement): MethodSpec {
        // payload rather than the declared payload: a value-class payload is compiled away, so
        // Java only ever sees the type it wrapped. flatName on the domain half too, so two nested
        // types of the same simple name cannot collide into one method signature.
        val methodBuilder = MethodSpec.methodBuilder(
            "map${element.name.flatName()}To${element.payload.identifier()}")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(element.payload.toJavaPoet())
            .addParameter(element.name.toJavaPoet(), "type")

        val (read, readArgs) = element.java.read("type").javaPoet()
        if (element.isPayloadPrimitive) {
            methodBuilder.addStatement("return $read", *readArgs)
        } else {
            methodBuilder
                .beginControlFlow("if (type == null)")
                .addStatement("return null")
                .endControlFlow()
                .addStatement("return $read", *readArgs)
        }

        return methodBuilder.build()
    }

    private fun createJavaMapFromPayloadMethod(element: ValidatedKspGeneratorElement): MethodSpec {
        // Resolved by the SPI rather than spelled here: `@JvmName` renames the factory, a companion
        // function without `@JvmStatic` is not on the type at all, and a value-class payload has to go
        // through the generated access shim. None of that is this generator's business.
        val (creation, creationArgs) = element.java.create("value").javaPoet()

        val methodBuilder = MethodSpec.methodBuilder(
            "map${element.payload.identifier()}To${element.name.flatName()}")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(element.name.toJavaPoet())
            .addParameter(element.payload.toJavaPoet(), "value")

        if (element.isPayloadPrimitive) {
            methodBuilder.addStatement("return $creation", *creationArgs)
        } else {
            methodBuilder
                .beginControlFlow("if (value == null)")
                .addStatement("return null")
                .endControlFlow()
                .addStatement("return $creation", *creationArgs)
        }

        return methodBuilder.build()
    }

    /**
     * Splits one of the SPI's Java expressions into a JavaPoet format string and the arguments its
     * slots stand for, so type names arrive as imports rather than as fully qualified text.
     */
    private fun PayloadExpr.javaPoet(): Pair<String, Array<Any>> {
        val (format, types) = asFormat($$"$T")
        return format to types.map { it.toJavaPoet() as Any }.toTypedArray()
    }
}
