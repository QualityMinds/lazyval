package com.qualityminds.lazyval.ksp.spi

import com.google.devtools.ksp.symbol.*
import org.jetbrains.annotations.ApiStatus


/**
 * A domain-primitive that passed validation, together with everything a generator needs to read its
 * payload and rebuild it.
 *
 * Accessors come in a Kotlin and a Java flavour because the two languages see different names. Kotlin
 * output reads the declaration as written; Java output has to use the name the member actually carries
 * in the bytecode, which `@JvmName` and `internal` both move. Those JVM names are resolved once during
 * validation and handed over as [javaAccessorName] and [javaFactoryPath], so no generator has to guess
 * them from the Kotlin declaration — guessing is what used to make `@JvmName` emit Java that could not
 * compile.
 *
 * @param javaAccessorName bytecode name of the getter or accessor function Java output should call
 * @param javaFactoryPath the dot-path from the Java type down to the factory method, for Java output to
 *                        append to the type name — `"of"` for a `@JvmStatic` function,
 *                        `"Companion.of"` (or the companion's own name) for one without, since Kotlin
 *                        then compiles it onto the companion class rather than onto the type. `null`
 *                        when there is no factory and Java output should call the constructor. Kotlin
 *                        output has no use for it and should call [objectCreation] instead: in Kotlin a
 *                        companion function is reachable through the type either way.
 */
@ApiStatus.Experimental()
data class ValidatedKspGeneratorElement(
    val element: KSClassDeclaration,
    val wrappedProperty: WrappedProperty,
    val factoryMethod: KSFunctionDeclaration?,
    private val accessorMethod: KSFunctionDeclaration?,
    private val javaAccessorName: String,
    val javaFactoryPath: String?
){
    /**
     * A type name
     */
    val typeName: TypeName = TypeName.from(element)

    /**
     * For Kotlin sources, this will yield the type accessor (being it a property or a function)
     */
    val kotlinAccessor: String by lazy {
        when {
        // If there's an explicit accessor method, use it (make sure to call as a function with '()')
        accessorMethod != null -> "${accessorMethod.simpleName.asString()}()"
        // For data classes, properties are automatically accessible
        element.modifiers.contains(Modifier.DATA) -> wrappedProperty.name
        // For regular classes, assume properties are accessible (make sure to call as a property without '()')
        else -> wrappedProperty.name}
    }

    /**
     * In case Java sources need to be generated, the call that reads the payload — the JavaBean getter
     * Kotlin synthesizes, an explicit accessor function, or whatever name `@JvmName` gave either of
     * them. Always a method invocation; a payload with no getter at all is rejected during validation
     * rather than described here.
     */
    val javaAccessor: String = "$javaAccessorName()"

    fun objectCreation(parameterName: String): String {
        return if (factoryMethod != null) {
            "${typeName}.${factoryMethod.simpleName.asString()}(${parameterName})"
        } else {
            "${typeName}(${parameterName})"
        }
    }
}

/**
 * Inspired by Javapoet's 'TypeName', provides a simple way to represent a type name which might be nested.
 */
data class TypeName(val value: String){

    /**
     * Use this to create a save name for a class or method. This is important when inner-types are encountered: the
     * class/method and filename must not contain dots.
     * In the case of inner-types, the enclosing type(s) are concatenated to make sure they are unique.
     * @return the simple name of this type, without dots from nested types.
     */
    val name: String = value.replace(".", "")

    override fun toString(): String {
        return value
    }

    companion object {
        private fun getSimpleNameEnclosed(e: KSClassDeclaration, remainder: String): String {
            val enclosing = e.parentDeclaration
            if(enclosing is KSClassDeclaration){
                return getSimpleNameEnclosed(enclosing, "${enclosing.simpleName.asString()}.${remainder}")
            }
            return remainder
        }

        fun from(e: KSClassDeclaration): TypeName {
            return TypeName(getSimpleNameEnclosed(e, e.simpleName.asString()))
        }
    }
}


@Suppress("unused") // api-surface
data class WrappedProperty(val property: KSPropertyDeclaration) {
    val type: KSType = property.type.resolve()
    val name: String = property.simpleName.asString()

    fun isPrimitive(): Boolean {
        return when (type.declaration.simpleName.asString()) {
            "Int", "Long", "Short", "Byte", "Double", "Float", "Boolean", "Char" -> true
            else -> false
        }
    }

    fun isBoxedPrimitive(): Boolean {
        return when (type.declaration.qualifiedName?.asString()) {
            "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte",
            "java.lang.Double", "java.lang.Float", "java.lang.Boolean", "java.lang.Character" -> true
            else -> false
        }
    }
}