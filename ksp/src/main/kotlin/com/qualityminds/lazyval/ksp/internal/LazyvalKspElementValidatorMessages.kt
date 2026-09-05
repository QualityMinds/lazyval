@file:Suppress("TooManyFunctions")
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
 * Why a `value class` payload cannot be unwrapped. Every one of these is a shape Kotlin permits, and
 * each would otherwise surface as an error inside the generated shim — a Kotlin error, since the shim
 * is Kotlin, which makes it no less of a broken build.
 *
 * The advice points at the value class rather than at the domain-primitive: it is the value class's own
 * contract that is in the way, and saying otherwise would send the author to edit the wrong file.
 */
internal fun valueClassPrivatePropertyMessage(payloadType: KSType, propertyName: String): String =
    "Payload type '${payloadType.shortName()}' is a value class whose property '$propertyName' is not " +
            "public, so nothing can read the value it wraps. " +
            "Make the property public, or use a different payload type."

/**
 * The same obstacle in a declaration this compilation does not hold the source of — a standard-library
 * value class such as `kotlin.time.Duration`, whose `rawValue` is private. Widening it is not among the
 * author's options, so the advice cannot be to widen it; the only honest move left is a payload type
 * they can shape.
 */
internal fun valueClassForeignPropertyMessage(payloadType: KSType, propertyName: String): String =
    "Payload type '${payloadType.shortName()}' is a value class from outside this compilation whose " +
            "property '$propertyName' is not public, so nothing can read the value it wraps and the " +
            "declaration cannot be widened from here. " +
            "Use a payload type this project declares, or the plain type " +
            "${payloadType.shortName()} converts to."

/**
 * A nullable underlying type is refused rather than unwrapped. Generated code carries what the value
 * class wraps *in place of* the value class, so a nullable one would hand it a nullable payload — the
 * very thing the payload rule above forbids, for the same reason: a value whose payload may be absent
 * is not a value. Absence belongs to the reference the caller holds, not inside the wrapper.
 */
internal fun valueClassNullablePayloadMessage(payloadType: KSType, propertyName: String): String =
    "Payload type '${payloadType.shortName()}' is a value class whose property '$propertyName' is " +
            "nullable, and generated code carries that value in place of the value class. " +
            "Make the property non-nullable; a value that may be absent belongs to the reference at " +
            "the call site, not inside the value class."

/** No factory and no reachable constructor, so the value cannot be rebuilt from its payload. */
internal fun valueClassUnconstructableMessage(payloadType: KSType): String =
    "Payload type '${payloadType.shortName()}' is a value class that can neither be constructed nor " +
            "built through a factory: its constructor is not accessible and it declares no public " +
            "single-argument factory returning ${payloadType.shortName()}. " +
            "Widen the constructor, or add a factory function in its companion object."

/**
 * A nullable result is allowed for the domain-primitive's own factory and refused for a value class's,
 * because the two are called in different places. The domain-primitive's answers a caller who can hold
 * the `null`; this one is called from inside the wrapping expression, where the enclosing constructor
 * wants the value class and a `null` has nowhere to go.
 */
internal fun valueClassNullableFactoryMessage(payloadType: KSType, name: String): String =
    "Payload type '${payloadType.shortName()}' is a value class whose factory function '$name' " +
            "returns ${payloadType.shortName()}?, but generated code has to rebuild a non-null " +
            "${payloadType.shortName()} from the payload it carries. " +
            "Make the factory return ${payloadType.shortName()}, or throw instead of returning null."

/** Mirrors the domain-primitive rule: with several candidates there is no single one to pick. */
internal fun valueClassAmbiguousFactoryMessage(payloadType: KSType, names: List<String>): String =
    "Payload type '${payloadType.shortName()}' is a value class with multiple matching factory " +
            "functions, so Lazyval cannot tell which one rebuilds it. " +
            "Please check functions ${names.joinToString(", ")}."

/** The value class is shaped in a way KSP cannot describe; nothing more specific can be said. */
internal fun valueClassUnreadableMessage(payloadType: KSType): String =
    "Payload type '${payloadType.shortName()}' is a value class Lazyval cannot inspect: it exposes no " +
            "single-property primary constructor. " +
            "Use a different payload type."

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
