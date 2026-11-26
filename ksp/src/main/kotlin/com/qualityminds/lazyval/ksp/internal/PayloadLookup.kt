package com.qualityminds.lazyval.ksp.internal

import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSBuiltIns
import com.google.devtools.ksp.symbol.*

/**
 * Accepted in place of `@kotlin.jvm.Transient`, which static analysis tends to flag on a type that is not
 * `Serializable`. These two because of their wide use.
 */
private val TRANSIENT_ANNOTATIONS = setOf(
    "kotlin.jvm.Transient",
    "jakarta.persistence.Transient",
    "org.springframework.data.annotation.Transient")

/**
 * What a class offers as its payload: exactly one property, or the reason there is not exactly one. Found by
 * Lazyval's conventions and judged by [LazyvalKspElementValidator], which reports every case but [Found].
 *
 * The conventions: every property that is neither static nor declared derived, paired with the accessor
 * generated code reads it through, which [AccessorLookup] finds. A property counts as derived when it, its getter
 * or its accessor carries one of [TRANSIENT_ANNOTATIONS]: Kotlin routes `@get:Transient` — and any Java annotation
 * whose only applicable target is `METHOD` — onto the getter, while an external Java type's accessor is a function
 * of its own, so all three have to be consulted, and accessors are paired before anything is filtered.
 */
internal sealed interface PayloadLookup {

    /** No property that is neither static nor declared derived. */
    data object None : PayloadLookup

    /**
     * Properties that would hold a payload, but none that generated code can read. Kept apart from [None]
     * because hiding what a class wraps is a different mistake from having nothing to wrap, and worth naming the
     * property for.
     */
    data class Unreadable(val properties: List<KSPropertyDeclaration>) : PayloadLookup

    /** More than one property generated code can read. */
    data class Several(val properties: List<KSPropertyDeclaration>) : PayloadLookup

    /**
     * The payload.
     *
     * @param accessor the public function generated code reads it through, or `null` where it reads the
     *                 property's getter
     * @param type the payload type as declared, which for a `value class` payload is the wrapper
     * @param valueClass whether, and how, [type] is a value class Lazyval unwraps — looked up once, because a rule
     *                   judges it and the element describes generated code by it
     */
    data class Found(
        val property: KSPropertyDeclaration,
        val accessor: KSFunctionDeclaration?,
        val type: KSType,
        val valueClass: ValueClassInspection
    ) : PayloadLookup

    companion object {

        fun of(type: KSClassDeclaration, builtIns: KSBuiltIns): PayloadLookup {
            val methods = type.getAllFunctions().toList()
            // Tier 3 (type-only match) only runs for external Java declarations. Applying it to Kotlin would
            // wrongly pair a data class's property with its synthesized `component1()` accessor and break the
            // JavaBean output Mapstruct/JPA/etc. expect.
            val isExternalJavaType = type.origin == Origin.JAVA || type.origin == Origin.JAVA_LIB
            val pairs = type.getAllProperties()
                .filter { !it.isStatic() }
                .map { PropertyAccessorPair(it, findAccessor(it, methods, isExternalJavaType)) }
                .filter { !it.property.isTransient(it.accessor) }
                .toList()
            val readable = pairs.filter { it.isReadable }
            return when {
                pairs.isEmpty() -> None
                readable.isEmpty() -> Unreadable(pairs.map { it.property })
                readable.size > 1 -> Several(readable.map { it.property })
                else -> readable.single().let { (property, accessor) ->
                    val payloadType = property.type.resolve()
                    Found(property, accessor, payloadType, payloadType.inspectValueClass(builtIns))
                }
            }
        }
    }
}

/**
 * A property together with the accessor generated code should call, which is `null` when the property is read
 * directly.
 *
 * The accessor is allowed to be `null` so that properties of external Java types (e.g. java.time.Year's `year`)
 * still get a chance to be paired with a JavaBean accessor. KSP reports `getter=null`/`hasBackingField=false` for
 * those synthesized properties even though a matching bean getter exists as a separate function.
 */
private data class PropertyAccessorPair(val property: KSPropertyDeclaration, val accessor: KSFunctionDeclaration?) {

    /**
     * Whether generated code can actually read this property, which is what makes the "exactly one accessible
     * property" contract true rather than aspirational.
     *
     * A pair that found an accessor is always readable — [AccessorLookup.accessorCandidates] only ever offers
     * public functions. Without one the property is read through the getter Kotlin synthesizes, and that route
     * exists only for public properties. Generated code is emitted into its own package, so a property reachable
     * neither way would yield source that does not compile.
     */
    val isReadable: Boolean
        get() = accessor != null || property.isPublic()
}

private fun KSPropertyDeclaration.isStatic(): Boolean =
    Modifier.CONST in modifiers || (parent as? KSClassDeclaration)?.isCompanionObject == true

/** True when the property, its getter, or a separately declared bean accessor is marked transient. */
private fun KSPropertyDeclaration.isTransient(accessor: KSFunctionDeclaration?): Boolean =
    annotations.hasTransientMarker() ||
            getter?.annotations?.hasTransientMarker() == true ||
            accessor?.annotations?.hasTransientMarker() == true

private fun Sequence<KSAnnotation>.hasTransientMarker(): Boolean =
    any { annotation ->
        annotation.shortName.asString() == "Transient" &&
                annotation.annotationType.resolve().declaration.qualifiedName?.asString() in TRANSIENT_ANNOTATIONS
    }
