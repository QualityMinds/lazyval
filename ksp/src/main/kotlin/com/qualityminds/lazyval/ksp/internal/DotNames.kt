package com.qualityminds.lazyval.ksp.internal

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.qualityminds.lazyval.naming.DotName
import com.qualityminds.lazyval.naming.Payload

/**
 * Reads a declaration's name off the compiler, which is the one place the package/nesting split is
 * known rather than guessed.
 *
 * KSP reports a nested declaration's package on the declaration itself, and its enclosing types only
 * through [KSDeclaration.parentDeclaration] — so the two halves have to be assembled here. Doing it
 * once, at the boundary, is why nothing downstream needs a `bestGuess`-style heuristic; see [DotName].
 */
internal fun KSDeclaration.toDotName(): DotName {
    val simpleNames = ArrayDeque<String>()
    var current: KSDeclaration? = this
    while (current != null) {
        simpleNames.addFirst(current.simpleName.asString())
        // Only a class encloses a type name. A function or file parent contributes nothing Java or
        // Kotlin could spell as a qualifier, so the walk stops there.
        current = current.parentDeclaration as? KSClassDeclaration
    }
    return DotName(packageName.asString(), simpleNames.toList())
}

/**
 * Reads the payload type's name as the JVM sees it, which is not always how Kotlin writes it.
 *
 * Kotlin's own scalar types map onto JVM primitives, and `kotlin.String` onto `java.lang.String`; both
 * are decided here rather than in each generator, which is what lets Java and Kotlin output derive the
 * same identifier from the same payload. Matching on the qualified name rather than the simple one so
 * that a user type of its own called `Int` stays a declared type.
 *
 * Anything else keeps its Kotlin declaration name, including the types Kotlin maps to a JVM collection
 * — `kotlin.collections.List` is reported as declared under that name rather than as `java.util.List`.
 * No stock generator names a collection payload, so nothing turns on it today.
 */
internal fun KSType.toPayload(): Payload =
    when (declaration.qualifiedName?.asString()) {
        "kotlin.Int" -> Payload.Primitive(Payload.Kind.INT)
        "kotlin.Long" -> Payload.Primitive(Payload.Kind.LONG)
        "kotlin.Short" -> Payload.Primitive(Payload.Kind.SHORT)
        "kotlin.Byte" -> Payload.Primitive(Payload.Kind.BYTE)
        "kotlin.Double" -> Payload.Primitive(Payload.Kind.DOUBLE)
        "kotlin.Float" -> Payload.Primitive(Payload.Kind.FLOAT)
        "kotlin.Boolean" -> Payload.Primitive(Payload.Kind.BOOLEAN)
        "kotlin.Char" -> Payload.Primitive(Payload.Kind.CHAR)
        "kotlin.String" -> Payload.Declared(DotName.of("java.lang", "String"))
        // Through the declaration's own name, so a nested payload type keeps its enclosing ones.
        else -> Payload.Declared(declaration.toDotName())
    }
