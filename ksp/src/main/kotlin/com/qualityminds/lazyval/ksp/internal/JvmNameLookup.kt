package com.qualityminds.lazyval.ksp.internal

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*

private const val JVM_STATIC_ANNOTATION = "kotlin.jvm.JvmStatic"

/**
 * Answers what a member is *called* in the bytecode, which is the one thing about it that Java output
 * cannot read off the Kotlin declaration. The companion question to [AccessorLookup], which answers
 * *which* member exposes the payload; together they settle what a generated Java call looks like.
 *
 * Deliberately not part of [AccessorLookup], whose contract is to be a pure, KSP-free heuristic that
 * unit tests can drive without constructing KSP symbols. Every answer here comes from [Resolver], so
 * moving it there would cost that file the property it is organised around.
 *
 * Kotlin output has no use for any of this: it reads declarations as written, and `@JvmName` moves the
 * JVM name only.
 */
internal class JvmNameLookup(private val resolver: Resolver) {

    /**
     * The bytecode name generated Java has to call to read the payload. Differs from the property name
     * whenever `@JvmName` renames the getter, and also under Kotlin's own convention of keeping an
     * `is`-prefixed property's name as its getter (`isActive`, not `getIsActive`).
     *
     * Falls back to the JavaBean spelling when the name cannot be resolved: [jvmName] returns `null`
     * only on a resolution failure, and that spelling is what Lazyval assumed before it started asking
     * — no worse than it used to be, rather than nothing at all.
     */
    fun javaAccessorName(property: KSPropertyDeclaration, accessor: KSFunctionDeclaration?): String {
        if (accessor != null) {
            return jvmName(accessor) ?: accessor.simpleName.asString()
        }
        property.getter?.let { getter -> jvmName(getter)?.let { return it } }
        return "get" + property.simpleName.asString().replaceFirstChar { it.uppercase() }
    }

    /**
     * Where generated Java finds the factory, relative to the domain-primitive's type.
     *
     * `@JvmStatic` (and a Java `static`) put the function on the type itself, so its JVM name is the
     * whole path. Without it Kotlin compiles a companion function onto the companion class alone,
     * reachable from Java through the companion's field — which is what an author writing a plain
     * `companion object { fun of(..) }` gets, instead of a call to a method that is not there.
     *
     * `null` when there is no factory and Java output should call the constructor.
     */
    fun javaFactoryPath(factory: KSFunctionDeclaration?): String? {
        if (factory == null) {
            return null
        }
        val jvmName = jvmName(factory) ?: factory.simpleName.asString()
        if (factory.annotations.hasMarker(JVM_STATIC_ANNOTATION) ||
            Modifier.JAVA_STATIC in factory.modifiers) {
            return jvmName
        }
        val companion = factory.parentDeclaration as? KSClassDeclaration
        return if (companion?.isCompanionObject == true) {
            "${companion.simpleName.asString()}.$jvmName"
        } else {
            jvmName
        }
    }

    /**
     * The name [function] carries in the bytecode. Differs from the Kotlin name whenever `@JvmName`
     * renames it, and carries a `$module` suffix when the function is `internal` — which is why an
     * internal factory is asked for `@JvmName` rather than having that suffix hard-coded into
     * generated Java.
     *
     * `null` signals a resolution failure rather than "no JVM name"; callers fall back to the Kotlin
     * name.
     */
    @OptIn(KspExperimental::class)
    private fun jvmName(function: KSFunctionDeclaration): String? = resolver.getJvmName(function)

    /**
     * The name [accessor] carries in the bytecode. Mirrors [jvmName] for a property's getter, where
     * `@get:JvmName` plays the same role `@JvmName` does on a function.
     */
    @OptIn(KspExperimental::class)
    private fun jvmName(accessor: KSPropertyAccessor): String? = resolver.getJvmName(accessor)
}
