package com.acme.lazyval.generator;

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import de.qualityminds.lazyval.ksp.ValidatedKspGeneratorElement
import de.qualityminds.lazyval.ksp.spi.FilePerTypeGenerator
import de.qualityminds.lazyval.ksp.spi.GeneratorResult
import de.qualityminds.lazyval.ksp.spi.SpiGenerator
import java.util.Collections.emptyList

class UtilsGenerator : FilePerTypeGenerator {


    companion object {
        const val OPTION_GENERATED_PACKAGE = "acme.some.lazyval.generatedPackage"
    }

    override fun generatorId(): String = "acme-utils"

    override fun requiredClasspath(): Collection<String> = emptyList()

    override fun generateFilePerType(
        validatedElement: ValidatedKspGeneratorElement,
        userSettings: SpiGenerator.Settings
    ): GeneratorResult {
        val element = validatedElement.element
        val wrappedType = validatedElement.wrappedType
        val lazyvalTypeName = element.toClassName()

        val wrappedTypeName = if (wrappedType.isBoxedPrimitive() || wrappedType.isPrimitive()) {
            wrappedType.toTypeName().copy(nullable = false)
        } else {
            wrappedType.toTypeName()
        }

        if(wrappedTypeName != ClassName("kotlin", "String")){
                return GeneratorResult.Nothing
        }

        val utilsClassName = "${element.simpleName.asString()}Utils"

        val extensionFun = FunSpec.builder("toUpperCase")
            .receiver(lazyvalTypeName)
            .returns(String::class)
            .addStatement("return this.value.uppercase()")
            .build()

        // Determine package
        val packageName = userSettings.options[OPTION_GENERATED_PACKAGE]
            ?: "${extractRootPackage(element)}.test"

        val kotlinSpec = FileSpec.builder(packageName, utilsClassName)
            .addFunction(extensionFun)
            .build()
        return GeneratorResult.Kotlin(
            GeneratorResult.Metadata(packageName, utilsClassName),
            kotlinSpec.toString())
    }

}
