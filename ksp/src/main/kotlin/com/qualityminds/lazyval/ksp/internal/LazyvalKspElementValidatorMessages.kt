package com.qualityminds.lazyval.ksp.internal

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.*


/**
 * Why generated code cannot read a property, phrased as the change the author has to make. Naming
 * the property is the point: the author can see it in their editor, so a message claiming nothing
 * was found reads as a bug in Lazyval rather than as a rule of Lazyval.
 */
internal fun unreachablePropertyMessage(property: KSPropertyDeclaration): String {
    val visibility = property.getVisibility()
    return "Property '${property.simpleName.asString()}' is ${visibility.keyword()} and cannot be read " +
            "from generated code, which is emitted into another package." +
            visibility.manglingNote(mangled = "getter", declaration = "property") +
            " Make the property public, or add a public accessor function."
}

/**
 * The advice is the mirror image of [nonPublicConstructorMessage]: an author who wrote a factory
 * meant that to be the way in, so they are pointed at widening it rather than at the constructor
 * they deliberately hid behind it.
 *
 * Unlike a constructor, a function is subject to the same JVM name mangling as a property getter,
 * so `internal` fails here for the reason [unreachablePropertyMessage] spells out.
 */
internal fun nonPublicFactoryMessage(factory: KSFunctionDeclaration): String {
    val visibility = factory.getVisibility()
    val parameterType = factory.parameters[0].type.resolve().shortName()
    return "Factory function '${factory.simpleName.asString()}($parameterType)' is ${visibility.keyword()} and " +
            "cannot be called from generated code, which is emitted into another package." +
            visibility.manglingNote(mangled = "function", declaration = "function") +
            " Make the factory function public, or add a public constructor."
}

/**
 * Mirrors [unreachablePropertyMessage], down to the naming of the visibility: the same package
 * boundary is at fault, so the same sentence should explain it.
 */
internal fun nonPublicConstructorMessage(
    classDeclaration: KSClassDeclaration,
    constructor: KSFunctionDeclaration,
    visibility: Visibility
): String {
    val parameterType = constructor.parameters[0].type.resolve().shortName()
    return "Constructor '${classDeclaration.simpleName.asString()}($parameterType)' is ${visibility.keyword()} and " +
            "cannot be called from generated code, which is emitted into another package. " +
            "Make the constructor public, or add a factory function in the companion object."
}

/** Nothing to point at, so this one names the type and the payload it cannot be rebuilt from. */
internal fun missingConstructorMessage(classDeclaration: KSClassDeclaration, payloadType: KSType): String =
    "Class '${classDeclaration.simpleName.asString()}' cannot be reconstructed from its payload: " +
            "no constructor takes a single ${payloadType.shortName()}. " +
            "Add one, or a factory function in the companion object."

/** How every diagnostic names a visibility. Kotlin has no keyword for Java's default. */
private fun Visibility.keyword(): String = when (this) {
    Visibility.PRIVATE -> "private"
    Visibility.PROTECTED -> "protected"
    Visibility.INTERNAL -> "internal"
    Visibility.JAVA_PACKAGE -> "package-private"
    else -> "not public"
}

/**
 * `internal` is the case that looks like it should work — it is visible to the whole module,
 * generated sources included, and only the mangled JVM name gives it away — so every message about
 * it says so. Contrast an internal *class*, which Java can use because class names are not mangled;
 * see KspClassInspection.isAccessible.
 *
 * [mangled] names what Kotlin renames (a property's `getter`, or the `function` itself) and
 * [declaration] what the author wrote. Empty for every other visibility.
 */
private fun Visibility.manglingNote(mangled: String, declaration: String): String =
    if (this == Visibility.INTERNAL) {
        " Part of that output is Java, which cannot call the name-mangled $mangled Kotlin " +
                "emits for an internal $declaration."
    } else {
        ""
    }

/** Types read better unqualified in Kotlin diagnostics, the way the language itself writes them. */
private fun KSType.shortName(): String = declaration.simpleName.asString()
