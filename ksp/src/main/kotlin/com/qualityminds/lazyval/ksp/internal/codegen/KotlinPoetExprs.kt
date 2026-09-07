package com.qualityminds.lazyval.ksp.internal.codegen

import com.qualityminds.lazyval.ksp.spi.PayloadExpr
import com.qualityminds.lazyval.naming.DotName
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock

/**
 * Hands a [DotName] to KotlinPoet, which wants the package and the simple names separately.
 *
 * The KotlinPoet twin of [toJavaPoet]; the same reason applies, that a [DotName] already carries the
 * split and so nothing has to guess where the package ends.
 */
internal fun DotName.toKotlinPoet(): ClassName = ClassName(packageName(), simpleNames())

/**
 * Renders one of the SPI's Kotlin expressions as a [CodeBlock], so every type it names arrives as an
 * import rather than as fully qualified text.
 *
 * Worth preferring over interpolating [PayloadExpr.asSource] into a statement, which is what the stock
 * generators used to do: KotlinPoet adds an import only for a type handed to it as `%T`, and raw
 * statement text carries none. `asSource()` spells the type qualified for exactly that reason, so both
 * routes compile — this one is simply the route that keeps the output short.
 */
internal fun PayloadExpr.kotlinPoet(): CodeBlock {
    val (format, types) = asFormat("%T")
    return CodeBlock.of(format, *types.map { it.toKotlinPoet() }.toTypedArray())
}
