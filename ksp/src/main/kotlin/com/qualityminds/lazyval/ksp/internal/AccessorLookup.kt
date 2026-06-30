package com.qualityminds.lazyval.ksp.internal

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier

/**
 * Pure, KSP-free heuristic for pairing a value-type's property with the accessor method that exposes
 * it to generated code. The matching rules ([accessorCandidates], [findAccessor]) are fully
 * data-driven so they can be unit-tested without constructing KSP symbols. The boundary adapters
 * ([toShape], [matchesShape]) — the only KSP-aware code — live here too so the entire
 * KSP-to-heuristic contract is in one place.
 */
object AccessorLookup {

    /**
     * Inherited from `Any`/`Object` by every class. `hashCode(): Int` and `toString(): String`
     * collide with common wrapped-property types and would otherwise be picked up by the tier-3
     * type-only match, pairing a field with the wrong getter.
     */
    val OBJECT_METHOD_NAMES = setOf("equals", "hashCode", "toString")

    /** KSP-free identity of a property: the name as KSP exposes it, and the resolved type's FQN. */
    data class Property(val name: String, val typeFqn: String)

    /**
     * KSP-free identity of a candidate method, carrying the structural flags the candidate filter
     * needs. [isStatic] is decided at [toShape] time — it captures any form of non-instance
     * membership (companion-object methods, `JAVA_STATIC` modifier) so the heuristic doesn't need
     * to know about KSP's specifics.
     */
    data class Method(
        val name: String,
        val returnTypeFqn: String?,
        val parameterCount: Int,
        val isStatic: Boolean,
    )

    /**
     * Filters [methods] to those plausible as a value-type accessor: instance (non-static),
     * zero-arg, non-Unit return, not an inherited `Object` method.
     */
    fun accessorCandidates(methods: List<Method>): List<Method> = methods.filter {
        !it.isStatic &&
                it.parameterCount == 0 &&
                it.returnTypeFqn != null &&
                it.returnTypeFqn != "kotlin.Unit" &&
                it.name !in OBJECT_METHOD_NAMES
    }

    /**
     * Three-tier match, in order of specificity:
     *  1. **Name match** — method named exactly after the property (idiomatic Kotlin).
     *  2. **JavaBean naming** — `get<Property>` / `is<Property>` matching by capitalized name.
     *  3. **Type-only fallback** — first candidate whose return type matches; gated to
     *     [isExternalJavaType] so Kotlin data classes' synthesized `component1()` doesn't get
     *     misidentified as the accessor.
     */
    fun findAccessor(
        property: Property,
        candidates: List<Method>,
        isExternalJavaType: Boolean,
    ): Method? {
        val cap = property.name.replaceFirstChar { it.uppercase() }
        return candidates.firstOrNull {
            it.returnTypeFqn == property.typeFqn &&
                    (it.name == property.name || it.name == "${property.name}()")
        } ?: candidates.firstOrNull {
            it.returnTypeFqn == property.typeFqn &&
                    (it.name == "get$cap" || it.name == "is$cap")
        } ?: if (isExternalJavaType) {
            candidates.firstOrNull { it.returnTypeFqn == property.typeFqn }
        } else null
    }
}

/**
 * High-level orchestrator: given a property and the full set of methods declared on its enclosing
 * type, returns the accessor that exposes the property, or `null` if none is found. Composes
 * [toProperty], [toShape], [AccessorLookup.accessorCandidates], the shape-based
 * [AccessorLookup.findAccessor], and [matchesShape] into a single call so the validator doesn't
 * have to thread the intermediate candidate-shape list through.
 *
 * @param isExternalJavaType whether the enclosing class is declared in a Java source/jar; only then
 *                           does the type-only fallback tier fire. Computed at the validator level
 *                           from [com.google.devtools.ksp.symbol.KSClassDeclaration.origin].
 */
fun findAccessor(
    property: KSPropertyDeclaration,
    methods: List<KSFunctionDeclaration>,
    isExternalJavaType: Boolean,
): KSFunctionDeclaration? {
    val candidates = AccessorLookup.accessorCandidates(methods.map { it.toShape() })
    return AccessorLookup.findAccessor(property.toProperty(), candidates, isExternalJavaType)
        ?.let { shape -> methods.firstOrNull { it.matchesShape(shape) } }
}

/** Maps a KSP property declaration to its [AccessorLookup.Property] shape. */
fun KSPropertyDeclaration.toProperty(): AccessorLookup.Property = AccessorLookup.Property(
    name = simpleName.asString(),
    typeFqn = type.resolve().toFqn(),
)

/** Maps a KSP function declaration to its [AccessorLookup.Method] shape. */
fun KSFunctionDeclaration.toShape(): AccessorLookup.Method = AccessorLookup.Method(
    name = simpleName.asString(),
    returnTypeFqn = returnType?.resolve()?.toFqn(),
    parameterCount = parameters.size,
    isStatic = isStaticForLookup(),
)

/** Whether this KSP function corresponds to the given [AccessorLookup.Method] (used for look-back). */
fun KSFunctionDeclaration.matchesShape(shape: AccessorLookup.Method): Boolean =
    simpleName.asString() == shape.name && returnType?.resolve()?.toFqn() == shape.returnTypeFqn

/** FQN of a KSP type; falls back to `toString()` when no qualified name is available. */
internal fun KSType.toFqn(): String = declaration.qualifiedName?.asString() ?: toString()

// Unifies Kotlin (companion-object membership) and Java (JAVA_STATIC modifier) non-instance forms
// into a single "not an instance method" flag the heuristic can consume.
private fun KSFunctionDeclaration.isStaticForLookup(): Boolean =
    (parent is KSClassDeclaration && (parent as KSClassDeclaration).isCompanionObject) ||
            Modifier.JAVA_STATIC in modifiers
