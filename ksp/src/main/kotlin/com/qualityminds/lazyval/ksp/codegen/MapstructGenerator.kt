package com.qualityminds.lazyval.ksp.codegen

import com.google.devtools.ksp.symbol.KSType
import com.palantir.javapoet.*
import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.ValidatedKspGeneratorElement
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.SingleFileGenerator
import com.qualityminds.lazyval.ksp.spi.SpiGenerator
import javax.lang.model.element.Modifier

class MapstructGenerator : SingleFileGenerator {

    companion object {
        private const val OPTION_GENERATED_PACKAGE = "lazyval.mapstruct.generatedPackage"
    }

    override fun generatorId(): String = "mapstruct"

    override fun requiredClasspath(): Collection<String> = listOf("org.mapstruct.Mapper")

    override fun supportedOptions(): Set<String> {
        return setOf(OPTION_GENERATED_PACKAGE)
    }

    override fun generateSingleFile(
        validatedElements: NonEmptySet<ValidatedKspGeneratorElement>,
        userSettings: SpiGenerator.Settings
    ): GeneratorResult {
        // Build Java interface using JavaPoet
        val interfaceBuilder = TypeSpec.interfaceBuilder("LazyvalMapper")
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

        val firstElement = validatedElements.getAny().element
        val packageName = userSettings.options[OPTION_GENERATED_PACKAGE]
            ?: extractRootPackage(firstElement)

        val javaFile = JavaFile.builder(packageName, interfaceBuilder.build())
            .build()

        return GeneratorResult.Java(
            GeneratorResult.Metadata(packageName, "LazyvalMapper"),
            javaFile.toString())
    }

    private fun createJavaMapToWrappedTypeMethod(element: ValidatedKspGeneratorElement): MethodSpec {
        val className = element.element.simpleName.asString()
        val lazyvalTypeClassName = ClassName.get(
            element.element.packageName.asString(),
            element.element.simpleName.asString()
        )
        val wrappedTypeName = getJavaTypeName(element.wrappedType)

        val methodBuilder = MethodSpec.methodBuilder("map${className}ToWrappedType")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(wrappedTypeName)
            .addParameter(lazyvalTypeClassName, "type")

        // Use Java accessor method name for MapStruct
        if (element.wrappedType.isPrimitive()) {
            methodBuilder.addStatement("return type.${element.javaAccessorMethodName}")
        } else {
            methodBuilder
                .beginControlFlow("if (type == null)")
                .addStatement("return null")
                .endControlFlow()
                .addStatement("return type.${element.javaAccessorMethodName}")
        }

        return methodBuilder.build()
    }

    private fun createJavaMapFromWrappedTypeMethod(element: ValidatedKspGeneratorElement): MethodSpec {
        val className = element.element.simpleName.asString()
        val lazyvalTypeClassName = ClassName.get(
            element.element.packageName.asString(),
            element.element.simpleName.asString()
        )
        val wrappedTypeName = getJavaTypeName(element.wrappedType)

        // Store factory method in local variable to avoid smart cast issues
        val factoryMethod = element.factoryMethod
        val objectCreation = if (factoryMethod != null) {
            "${lazyvalTypeClassName.simpleName()}.${factoryMethod.simpleName.asString()}(value)"
        } else {
            "new ${lazyvalTypeClassName.simpleName()}(value)"
        }

        val methodBuilder = MethodSpec.methodBuilder("map$className")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(lazyvalTypeClassName)
            .addParameter(wrappedTypeName, "value")

        if (element.wrappedType.isPrimitive()) {
            methodBuilder.addStatement("return $objectCreation")
        } else {
            methodBuilder
                .beginControlFlow("if (value == null)")
                .addStatement("return null")
                .endControlFlow()
                .addStatement("return $objectCreation")
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
}