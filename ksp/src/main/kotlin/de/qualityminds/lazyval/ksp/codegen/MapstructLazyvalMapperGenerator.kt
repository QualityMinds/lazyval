package de.qualityminds.lazyval.ksp.codegen

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSType
import com.palantir.javapoet.*
import de.qualityminds.lazyval.ksp.LazyvalKspEnvironment
import de.qualityminds.lazyval.ksp.ValidatedKspGeneratorElement
import de.qualityminds.lazyval.ksp.spi.GeneratorResult
import de.qualityminds.lazyval.ksp.spi.SingleFileGenerator
import java.io.IOException
import javax.lang.model.element.Modifier

class MapstructKspGenerator : SingleFileGenerator {

    override fun generateSingleFile(
        validatedElements: List<ValidatedKspGeneratorElement>,
        environment: LazyvalKspEnvironment
    ): GeneratorResult {
        if (validatedElements.isEmpty()) {
            throw IllegalArgumentException("Cannot create mapper with empty elements")
        }

        if(!environment.isMapstructOnClasspath()) {
            environment.info("Mapstruct is not on classpath. Lazyval will not generate Mapper definition.")
            return GeneratorResult.Nothing
        }

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

        val firstElement = validatedElements.first().element
        val packageName = environment.getSettings().getMapstructPackage()
            ?: environment.extractRootPackage(firstElement)

        val javaFile = JavaFile.builder(packageName, interfaceBuilder.build())
            .build()

        return GeneratorResult.Java(JavaFileSpec(javaFile, packageName, "LazyvalMapper"))
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

    private fun KSType.isPrimitive(): Boolean {
        return when (declaration.simpleName.asString()) {
            "Int", "Long", "Short", "Byte", "Double", "Float", "Boolean", "Char" -> true
            else -> false
        }
    }
}

// Wrapper class for Java files
data class JavaFileSpec(
    val javaFile: JavaFile,
    val packageName: String,
    val fileName: String
) {
    fun writeTo(codeGenerator: CodeGenerator, dependencies: Dependencies) {
        try {
            val file = codeGenerator.createNewFile(dependencies, packageName, fileName, "java")
            file.write(javaFile.toString().toByteArray())
            file.close()
        } catch (e: IOException) {
            throw RuntimeException("Failed to write Java file", e)
        }
    }
}