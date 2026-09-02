package com.qualityminds.lazyval.ksp.internal

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.*


/**
 * The type itself rather than a member of it, and the first thing generated code needs: it has to be
 * able to name the domain-primitive before any property or constructor matters.
 *
 * `internal` is named as a fix because an internal *class* cannot be named by a caller outside the
 * module to begin with, so the public API Lazyval generates around it leaks nothing that was hidden.
 * Contrast [internalPropertyMessage], where the type stays public and the payload would leak.
 */
internal fun nonPublicTypeMessage(classDeclaration: KSClassDeclaration): String =
    "Type '${classDeclaration.simpleName.asString()}' is ${classDeclaration.getVisibility().keyword()} " +
            "and cannot be referenced from generated code, which is emitted into another package. " +
            "Make the type public or internal."

/**
 * Why generated code cannot read a property, phrased as the change the author has to make. Naming
 * the property is the point: the author can see it in their editor, so a message claiming nothing
 * was found reads as a bug in Lazyval rather than as a rule of Lazyval.
 *
 * `internal` is answered separately by [internalPropertyMessage], because the package boundary this
 * message blames is not what stands in its way.
 */
internal fun unreachablePropertyMessage(property: KSPropertyDeclaration): String {
    val visibility = property.getVisibility()
    if (visibility == Visibility.INTERNAL) {
        return internalPropertyMessage(property)
    }
    return "Property '${property.simpleName.asString()}' is ${visibility.keyword()} and cannot be read " +
            "from generated code, which is emitted into another package. " +
            "Make the property public, or add a public accessor function."
}

/**
 * `internal` on the payload is the one visibility that is not a reachability problem: generated code
 * lives in the same module and can reach it, and the JVM name it would have to call is recoverable.
 * The objection is a design one — everything Lazyval generates is public API that reads the payload
 * out, so accepting an internal payload would publish exactly what the author withheld.
 *
 * The accessor function is offered as more than a consolation: an `internal` property behind a
 * `public` accessor is supported, and says the same thing about the property while giving Lazyval
 * something it may legitimately call. Rejecting therefore costs no capability.
 */
internal fun internalPropertyMessage(property: KSPropertyDeclaration): String =
    "Property '${property.simpleName.asString()}' is internal, but the code Lazyval generates from it " +
            "is public — a mapper, a codec, a converter that reads the payload out — so accepting it " +
            "would publish the value 'internal' withholds. " +
            "Make the property public, or add a public accessor function and leave the property internal."

/**
 * `@JvmField` is the one way a payload can be `public` and still unreadable: the annotation exposes the
 * backing field and suppresses the getter, while generated code reads through an accessor rather than a
 * field. Unlike [unreachablePropertyMessage] the package boundary is not at fault, so this message does
 * not blame it — widening the property would change nothing.
 */
internal fun jvmFieldPropertyMessage(property: KSPropertyDeclaration): String =
    "Property '${property.simpleName.asString()}' is annotated @JvmField, which suppresses the getter " +
            "generated code reads the payload through. " +
            "Remove @JvmField, or add a public accessor function."

/**
 * A `value class` payload is compiled away rather than merely renamed, and in three ways at once: the
 * accessor's JVM name gains a signature hash, its type erases to the underlying one, and the primary
 * constructor becomes private behind a synthetic marker overload. Generated Java can express none of
 * them.
 *
 * The accessor function that rescues every other unreadable payload is no help here — a function
 * returning a value class is mangled just the same (`money-cgdmosI()`), so the message does not offer
 * it. Wrapping the underlying type directly is the only way out, which is why the advice names that
 * type where it can be resolved.
 */
internal fun valueClassPayloadMessage(payloadType: KSType): String {
    val advice = payloadType.underlyingValueClassType()
        ?.let { "Use $it as the payload instead." }
        ?: "Use its underlying type as the payload instead."
    return "Payload type '${payloadType.shortName()}' is a value class, which Kotlin compiles away: " +
            "the accessor's JVM name carries a signature hash, its type erases to the underlying one, " +
            "and the constructor becomes private. Generated Java can call none of them. $advice"
}

/**
 * The single property a value class wraps, which is what the author has to use instead. `null` when the
 * declaration cannot be read that way — an aliased or malformed value class — so the advice degrades to
 * naming no type rather than naming a wrong one.
 */
private fun KSType.underlyingValueClassType(): String? =
    (declaration as? KSClassDeclaration)
        ?.primaryConstructor
        ?.parameters
        ?.singleOrNull()
        ?.type
        ?.resolve()
        ?.shortName()

/**
 * The advice is the mirror image of [nonPublicConstructorMessage]: an author who wrote a factory
 * meant that to be the way in, so they are pointed at widening it rather than at the constructor
 * they deliberately hid behind it.
 *
 * `internal` is answered by [internalFactoryWithoutJvmNameMessage] and
 * [internalFactoryFromOtherModuleMessage] instead — it is supported under conditions this message
 * knows nothing about, so reaching here with it would give up too early.
 */
internal fun nonPublicFactoryMessage(factory: KSFunctionDeclaration): String {
    val visibility = factory.getVisibility()
    return "Factory function '${factory.signature()}' is ${visibility.keyword()} and " +
            "cannot be called from generated code, which is emitted into another package. " +
            "Make the factory function public, or add a public constructor."
}

/**
 * An `internal` factory is supported — restricting construction to the declaring module is a
 * reasonable thing to want, and the constructor half of it always was (see
 * [nonPublicConstructorMessage], which never rejects `internal`). A function differs from a
 * constructor only in having a name for Kotlin to mangle, and `@JvmName` settles that name.
 *
 * Lazyval asks for the annotation rather than emitting the mangled `of$module` itself, even though
 * that name is callable from Java. The suffix is the module name, which KSP and kotlinc are each told
 * separately by the build; if the two ever disagree, the generated call names a method that does not
 * exist and nothing in the processor could have noticed. An annotation the author wrote is verifiable,
 * a name the build supplies is not.
 */
internal fun internalFactoryWithoutJvmNameMessage(factory: KSFunctionDeclaration): String =
    "Factory function '${factory.signature()}' is internal, so Kotlin mangles its JVM name with the " +
            "module name, which generated Java cannot depend on. " +
            "Add @JvmName to give it a stable name, or make the function public."

/**
 * The one `internal` factory that no annotation rescues. `@JvmName` would make the function callable
 * from generated *Java*, because `internal` is public in the bytecode — but most generators emit
 * Kotlin, and there `internal` means what it says: unreachable from outside the declaring module.
 *
 * Only reachable through `@LazyvalConfiguration.externalTypes`, since a type from the current
 * compilation unit is by definition in this module.
 */
internal fun internalFactoryFromOtherModuleMessage(factory: KSFunctionDeclaration): String =
    "Factory function '${factory.signature()}' is internal to the module that declares it, so the " +
            "Kotlin sources Lazyval generates in this module cannot call it. " +
            "Make the function public where it is declared, or add a public constructor."

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

/** Types read better unqualified in Kotlin diagnostics, the way the language itself writes them. */
private fun KSType.shortName(): String = declaration.simpleName.asString()

/** How every factory diagnostic names the function, so the three of them stay comparable. */
private fun KSFunctionDeclaration.signature(): String =
    "${simpleName.asString()}(${parameters[0].type.resolve().shortName()})"
