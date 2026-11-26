package com.qualityminds.lazyval.ksp.internal

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.symbol.*

/**
 * How a class is rebuilt from its payload: the factory or constructor generated code calls, or the reason there
 * is none to call. Found by Lazyval's conventions and judged by [LazyvalKspElementValidator].
 *
 * The conventions: a factory is a function returning the class and taking a single parameter of the payload type,
 * whatever its name — a companion function in Kotlin, a static method on a Java type. Nullability is ignored when
 * matching one: a factory over `String?` still rebuilds a `String` payload, whether as an `ofNullable` returning
 * the class as nullable or as one yielding a default for `null`. The constructor is matched by the exact payload
 * type instead, nullability included, because a constructor cannot yield `null`. A factory is what generated code
 * calls wherever there is one, even with the constructor in reach, because a factory is where a class validates
 * what it is built from.
 */
internal sealed interface CreatorLookup {

    /** What generated code calls to rebuild the value, once the rules agree it may. */
    sealed interface Found : CreatorLookup {
        val declaration: KSFunctionDeclaration
    }

    /** The one factory. */
    data class Factory(override val declaration: KSFunctionDeclaration) : Found

    /** No factory, but a constructor taking exactly the payload type. */
    data class Constructor(override val declaration: KSFunctionDeclaration) : Found

    /** More than one factory, and no telling which one is meant to rebuild the value. */
    data class SeveralFactories(val factories: List<KSFunctionDeclaration>) : CreatorLookup

    /**
     * No factory, and the only constructor taking the payload takes it as nullable: a factory's signature, on a
     * declaration that cannot yield `null` the way a factory can.
     */
    data class NullableConstructor(val declaration: KSFunctionDeclaration) : CreatorLookup

    /** Neither a factory nor a constructor taking the payload. */
    data object Missing : CreatorLookup

    companion object {

        fun of(type: KSClassDeclaration, payloadType: KSType): CreatorLookup {
            val factories = findFactories(type, payloadType)
            return when {
                factories.size > 1 -> SeveralFactories(factories)
                factories.size == 1 -> Factory(factories.single())
                else -> {
                    val constructors = type.getConstructors().toList()
                    constructors.firstOrNull { it.singleParameterType() == payloadType }?.let(::Constructor)
                        ?: constructors.firstOrNull { it.singleParameterType()?.isNullableOf(payloadType) == true }
                            ?.let(::NullableConstructor)
                        ?: Missing
                }
            }
        }
    }
}

private fun findFactories(type: KSClassDeclaration, payloadType: KSType): List<KSFunctionDeclaration> {
    // Kotlin classes expose factories via companion objects. Java classes (e.g. java.time.Year.of) expose them as
    // JAVA_STATIC methods declared on the class itself. Scan both so external Java types can be used as
    // @LazyvalConfiguration.externalTypes — mirrors the APT side, which simply scans static methods.
    val companionFactories = type.declarations
        .filterIsInstance<KSClassDeclaration>()
        .firstOrNull { it.isCompanionObject }
        ?.declarations
        ?.filterIsInstance<KSFunctionDeclaration>()
        ?.toList()
        .orEmpty()
    val staticFactories = type.getDeclaredFunctions()
        .filter { Modifier.JAVA_STATIC in it.modifiers }
        .toList()
    val self = type.asStarProjectedType()
    return (companionFactories + staticFactories).filter { function ->
        val returnType = function.returnType?.resolve()
        function.parameters.size == 1 &&
                returnType != null && returnType.isOrNullable(self) &&
                function.parameters[0].type.resolve().isOrNullable(payloadType)
    }
}

private fun KSFunctionDeclaration.singleParameterType(): KSType? = parameters.singleOrNull()?.type?.resolve()

/** Whether this type is [other], or [other] made nullable: how a factory's parameter and result are matched. */
private fun KSType.isOrNullable(other: KSType): Boolean = this == other || isNullableOf(other)

private fun KSType.isNullableOf(other: KSType): Boolean = isMarkedNullable && makeNotNullable() == other
