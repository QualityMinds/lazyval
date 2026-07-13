package com.qualityminds.lazyval.ksp.internal.codegen

import com.google.devtools.ksp.symbol.KSType
import com.palantir.javapoet.*
import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.internal.codegen.GeneratedStamp.addGeneratedAnnotation
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import java.util.stream.Stream
import javax.lang.model.element.Modifier

class MapstructGenerator : Generator {

    companion object {
        private const val GENERATOR_ID = "mapstruct"
        private const val OPTION_GENERATED_PACKAGE = "lazyval.mapstruct.package"
    }

    override fun generatorId(): String = GENERATOR_ID

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
            interfaceBuilder.addMethod(createJavaMapToWrappedTypeMethod(element))
            interfaceBuilder.addMethod(createJavaMapFromWrappedTypeMethod(element))
        }

        val packageName = context.generatorPackage(OPTION_GENERATED_PACKAGE, null)

        val javaFile = JavaFile.builder(packageName, interfaceBuilder.build())
            .skipJavaLangImports(true)
            .build()

        return Stream.of(GeneratorResult.Java(
            GeneratorResult.Metadata(packageName, "LazyvalMapper"),
            javaFile.toString()))
    }

    private fun createJavaMapToWrappedTypeMethod(element: ValidatedKspGeneratorElement): MethodSpec {
        val className = element.typeName.name
        val lazyvalTypeClassName = nestedAwareClassName(element)
        val wrappedTypeName = getJavaTypeName(element.wrappedProperty.type)

        val methodBuilder = MethodSpec.methodBuilder("map${className}To${wrappedTypeName.asMethodName()}")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(wrappedTypeName)
            .addParameter(lazyvalTypeClassName, "type")

        // Use Java accessor method name for MapStruct
        if (element.wrappedProperty.isPrimitive()) {
            methodBuilder.addStatement("return type.${element.javaAccessor}")
        } else {
            methodBuilder
                .beginControlFlow("if (type == null)")
                .addStatement("return null")
                .endControlFlow()
                .addStatement("return type.${element.javaAccessor}")
        }

        return methodBuilder.build()
    }

    private fun createJavaMapFromWrappedTypeMethod(element: ValidatedKspGeneratorElement): MethodSpec {
        val className = element.element.simpleName.asString()
        val lazyvalTypeClassName = nestedAwareClassName(element)
        val wrappedTypeName = getJavaTypeName(element.wrappedProperty.type)

        // Store factory method in local variable to avoid smart cast issues
        val factoryMethod = element.factoryMethod
        val creationFormat: String
        val creationArgs: Array<Any>
        if (factoryMethod != null) {
            creationFormat = "\$T.${factoryMethod.simpleName.asString()}(value)"
            creationArgs = arrayOf(lazyvalTypeClassName)
        } else {
            creationFormat = "new \$T(value)"
            creationArgs = arrayOf(lazyvalTypeClassName)
        }

        val methodBuilder = MethodSpec.methodBuilder("map${wrappedTypeName.asMethodName()}To$className")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(lazyvalTypeClassName)
            .addParameter(wrappedTypeName, "value")

        if (element.wrappedProperty.isPrimitive()) {
            methodBuilder.addStatement("return $creationFormat", *creationArgs)
        } else {
            methodBuilder
                .beginControlFlow("if (value == null)")
                .addStatement("return null")
                .endControlFlow()
                .addStatement("return $creationFormat", *creationArgs)
        }

        return methodBuilder.build()
    }

    private fun getJavaTypeName(ksType: KSType): TypeName {
        return when (ksType.declaration.simpleName.asString()) {
            "Int" -> TypeName.INT
            "Long" -> TypeName.LONG
            "Short" -> TypeName.SHORT
            "Byte" -> TypeName.BYTE
            "Double" -> TypeName.DOUBLE
            "Float" -> TypeName.FLOAT
            "Boolean" -> TypeName.BOOLEAN
            "Char" -> TypeName.CHAR
            "String" -> ClassName.get("java.lang", "String")
            else -> {
                val packageName = ksType.declaration.packageName.asString()
                val simpleName = ksType.declaration.simpleName.asString()
                ClassName.get(packageName, simpleName)
            }
        }
    }

    /**
     * Builds a [ClassName] from the (possibly nested) type name.
     *
     * [com.qualityminds.lazyval.ksp.spi.TypeName.value] uses dot-separated simple names for nested types (e.g. `Ids.ProductId`)
     * and a plain simple name for top-level types (e.g. `Quantity`). JavaPoet expects the
     * enclosing and nested simple names as separate varargs, so we split on '.' here.
     * For top-level types this degrades to a single simple name with no nesting.
     */
    private fun nestedAwareClassName(element: ValidatedKspGeneratorElement): ClassName {
        val packageName = element.element.packageName.asString()
        val simpleNames = element.typeName.value.split(".")
        return ClassName.get(packageName, simpleNames.first(), *simpleNames.drop(1).toTypedArray())
    }
}

private fun TypeName.asMethodName(): String = when (this) {
    is ClassName -> simpleName()
    else -> toString()
}