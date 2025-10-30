package de.qualityminds.lazyval.ksp.codegen

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import de.qualityminds.lazyval.ksp.LazyvalKspEnvironment
import de.qualityminds.lazyval.ksp.ValidatedKspGeneratorElement
import de.qualityminds.lazyval.ksp.spi.GeneratorResult
import de.qualityminds.lazyval.ksp.spi.MultipleFilesGenerator
import de.qualityminds.lazyval.ksp.spi.SpiGenerator

class JpaKspGenerator : MultipleFilesGenerator {

    override fun generateFilePerType(
        validatedElement: ValidatedKspGeneratorElement,
        environment: LazyvalKspEnvironment
    ): GeneratorResult {

        if(!environment.isJpaOnClasspath()) {
            environment.info("JPA is not on classpath. Lazyval will not generate AttributeConverters.")
            return GeneratorResult.Nothing
        }

        val element = validatedElement.element
        val wrappedType = validatedElement.wrappedType
        val lazyvalTypeName = element.toClassName()

        // Handle primitive boxing for JPA generics
        val wrappedTypeName = if (wrappedType.isBoxedPrimitive() || wrappedType.isPrimitive()) {
            wrappedType.toTypeName().copy(nullable = false)
        } else {
            wrappedType.toTypeName()
        }

        val converterClassName = "${element.simpleName.asString()}AttributeConverter"

        // Build convertToDatabaseColumn method - use the Kotlin accessor method name
        val convertToDatabaseColumn = FunSpec.builder("convertToDatabaseColumn")
            .addModifiers(KModifier.OVERRIDE)
            .returns(wrappedTypeName.copy(nullable = true))
            .addParameter("type", lazyvalTypeName.copy(nullable = true))
            .apply {
                if (wrappedType.isPrimitive()) {
                    addStatement("return type?.${validatedElement.kotlinAccessorMethodName}")
                } else {
                    addStatement("return type?.${validatedElement.kotlinAccessorMethodName}")
                }
            }
            .build()

        // Store factory method in local variable to avoid smart cast issues
        val factoryMethod = validatedElement.factoryMethod
        val objectCreation = if (factoryMethod != null) {
            "${lazyvalTypeName.simpleName}.${factoryMethod.simpleName.asString()}(dbValue)"
        } else {
            "${lazyvalTypeName.simpleName}(dbValue)"
        }

        // Build convertToEntityAttribute method
        val convertToEntityAttribute = FunSpec.builder("convertToEntityAttribute")
            .addModifiers(KModifier.OVERRIDE)
            .returns(lazyvalTypeName.copy(nullable = true))
            .addParameter("dbValue", wrappedTypeName.copy(nullable = true))
            .apply {
                if (wrappedType.isPrimitive()) {
                    addStatement("return dbValue?.let { $objectCreation }")
                } else {
                    addStatement("return dbValue?.let { $objectCreation }")
                }
            }
            .build()

        // Build the converter class - no explicit visibility modifier needed (public by default)
        val converterClass = TypeSpec.classBuilder(converterClassName)
            .addAnnotation(
                AnnotationSpec.builder(ClassName("jakarta.persistence", "Converter"))
                    .addMember("autoApply = true")
                    .build()
            )
            .addSuperinterface(
                ClassName("jakarta.persistence", "AttributeConverter")
                    .parameterizedBy(
                        lazyvalTypeName.copy(nullable = true),
                        wrappedTypeName.copy(nullable = true)
                    )
            )
            .addFunction(convertToDatabaseColumn)
            .addFunction(convertToEntityAttribute)
            .build()

        // Determine package
        val packageName = environment.getSettings().getJpaConverterPackage()
            ?: "${environment.extractRootPackage(element)}.boundary.persistence"

        return GeneratorResult.Kotlin(FileSpec.builder(packageName, converterClassName)
            .addType(converterClass)
            .build())
    }

    private fun KSType.isPrimitive(): Boolean {
        return when (declaration.simpleName.asString()) {
            "Int", "Long", "Short", "Byte", "Double", "Float", "Boolean", "Char" -> true
            else -> false
        }
    }

    private fun KSType.isBoxedPrimitive(): Boolean {
        return when (declaration.qualifiedName?.asString()) {
            "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte",
            "java.lang.Double", "java.lang.Float", "java.lang.Boolean", "java.lang.Character" -> true
            else -> false
        }
    }
}