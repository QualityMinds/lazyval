package com.qualityminds.lazyval.ksp.internal.codegen

import com.qualityminds.lazyval.ksp.internal.ValueClassPayload
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

/**
 * Emits the Kotlin object generated Java goes through when the payload is a `value class`.
 *
 * Kotlin compiles a value class away: the payload accessor's JVM name carries a signature hash and the
 * enclosing constructor turns private in the bytecode, so Java can reach neither. The shim is ordinary
 * Kotlin on both counts and `@JvmStatic` puts its members where Java expects them.
 *
 * Written by the processor rather than by a generator, because several generators may need the same
 * shim and none of them should own it. That does mean a Kotlin-only project gets a shim it never calls
 * — one small `internal object`, which seemed a better trade than making the SPI element record
 * demand and turning it into mutable, order-dependent state.
 */
internal object JavaAccessShimCodegen {

    /** Kotlin's own `internal` keeps this out of the module's API while leaving it callable from Java. */
    fun generate(
        element: ValidatedKspGeneratorElement,
        payload: ValueClassPayload
    ): GeneratorResult.Kotlin {
        val shim = requireNotNull(element.javaAccessShim) {
            "generate() is only meaningful for an element whose payload needs a shim"
        }
        val domainType = element.element.toClassName()
        val underlying = payload.underlyingType.toTypeName()

        val read = FunSpec.builder(shim.readMember)
            .addAnnotation(JvmStatic::class)
            .addKdoc("Reads the unwrapped payload, which is all Java can see of %L.",
                payload.declaration.simpleName.asString())
            .addParameter(RECEIVER, domainType)
            .returns(underlying)
            .addStatement("return %L", element.kotlin.read(RECEIVER))
            .build()

        val create = FunSpec.builder(shim.createMember)
            .addAnnotation(JvmStatic::class)
            .addKdoc("Rebuilds %L from an unwrapped payload, re-wrapping through %L's own contract.",
                domainType.simpleName, payload.declaration.simpleName.asString())
            .addParameter(VALUE, underlying)
            .returns(domainType)
            .addStatement("return %L", element.kotlin.create(VALUE))
            .build()

        val typeSpec = TypeSpec.objectBuilder(shim.name.simpleName())
            .addModifiers(KModifier.INTERNAL)
            .addKdoc(KDOC, domainType.simpleName)
            .addFunction(read)
            .addFunction(create)
            .build()

        val fileSpec = FileSpec.builder(shim.name.packageName(), shim.name.simpleName())
            .addType(typeSpec)
            .build()

        return GeneratorResult.Kotlin(
            GeneratorResult.Metadata(shim.name.packageName(), shim.name.simpleName()),
            fileSpec.toString())
    }

    private const val RECEIVER = "instance"
    private const val VALUE = "payload"
    private const val KDOC =
        "Java-facing access to %L, whose payload is a Kotlin value class.\n\n" +
                "Kotlin compiles a value class away: the payload accessor's JVM name carries a " +
                "signature hash and the constructor is private in the bytecode, neither of which Java " +
                "can name. These two functions are the way in, and they trade in the type the value " +
                "class wraps, because that is all Java can see of it.\n\n" +
                "`internal`, so it is callable from generated Java in this compilation without " +
                "becoming part of the module's API."
}
