package com.qualityminds.lazyval.ksp.internal.codegen

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.TypeName
import com.qualityminds.lazyval.naming.DotName
import com.qualityminds.lazyval.naming.Payload

/**
 * Hands a [DotName] to JavaPoet, which wants the package and each simple name separately.
 *
 * The reason this is a one-liner and not a heuristic: `ClassName.bestGuess` has to work out where the
 * package ends by looking for the first capitalised segment, and gets a nested type wrong whenever that
 * convention is not followed. A [DotName] already carries the split, so nothing is inferred here.
 */
internal fun DotName.toJavaPoet(): ClassName =
    ClassName.get(packageName(), simpleNames().first(), *simpleNames().drop(1).toTypedArray())

/**
 * Hands a [Payload] to JavaPoet, which needs a primitive as one of its own constants and a
 * reference type as a [ClassName].
 *
 * The `when` is exhaustive over the sealed pair and over [Payload.Kind], so a new primitive kind is
 * a compile error here rather than something that quietly falls through to a wrong type. This used to
 * be a nine-branch `when` over Kotlin simple names, duplicated verbatim in two generators.
 */
internal fun Payload.toJavaPoet(): TypeName = when (this) {
    is Payload.Primitive -> when (kind) {
        Payload.Kind.BOOLEAN -> TypeName.BOOLEAN
        Payload.Kind.BYTE -> TypeName.BYTE
        Payload.Kind.SHORT -> TypeName.SHORT
        Payload.Kind.INT -> TypeName.INT
        Payload.Kind.LONG -> TypeName.LONG
        Payload.Kind.CHAR -> TypeName.CHAR
        Payload.Kind.FLOAT -> TypeName.FLOAT
        Payload.Kind.DOUBLE -> TypeName.DOUBLE
    }
    is Payload.Declared -> name.toJavaPoet()
}
