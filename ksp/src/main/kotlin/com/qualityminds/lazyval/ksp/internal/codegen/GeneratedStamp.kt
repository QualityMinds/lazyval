package com.qualityminds.lazyval.ksp.internal.codegen

import com.qualityminds.lazyval.ksp.spi.Generator
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeSpec
import kotlin.reflect.KClass
import com.palantir.javapoet.AnnotationSpec as JavaAnnotationSpec
import com.palantir.javapoet.ClassName as JavaClassName
import com.palantir.javapoet.TypeSpec as JavaTypeSpec


internal object GeneratedStamp {

    private val CLASS_NAME = ClassName("jakarta.annotation", "Generated")
    private val CLASS_NAME_JAVA = JavaClassName.get("jakarta.annotation", "Generated")

    internal fun TypeSpec.Builder.addGeneratedAnnotation(clazz: KClass<out Generator>, ctx: Generator.Context): TypeSpec.Builder {
        if(ctx.isOnClasspath("jakarta.annotation.Generated")) {
            val generatedAnnotation = AnnotationSpec.builder(CLASS_NAME)
                .addMember("%S", clazz.java.name)
                .build()
            this.addAnnotation(generatedAnnotation)
        }
        return this
    }

    internal fun JavaTypeSpec.Builder.addGeneratedAnnotation(clazz: KClass<out Generator>, ctx: Generator.Context): JavaTypeSpec.Builder {
        if(ctx.isOnClasspath("jakarta.annotation.Generated")) {
            val generatedAnnotation = JavaAnnotationSpec.builder(CLASS_NAME_JAVA)
                .addMember("value", $$"$S", clazz.java.name)
                .build()
            this.addAnnotation(generatedAnnotation)
        }
        return this
    }
}


