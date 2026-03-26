package com.qualityminds.lazyval.ksp.spi

import com.google.devtools.ksp.symbol.*
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Experimental()
data class ValidatedKspGeneratorElement(
    val element: KSClassDeclaration,
    val wrappedProperty: WrappedProperty,
    val factoryMethod: KSFunctionDeclaration?,
    private val accessorMethod: KSFunctionDeclaration?
){
    /**
     * The simple name of the annotated type.
     */
    val typeName: String = element.simpleName.asString()
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
     * In case Java sources need to be generated, this will yield the accessors' getter.
     */
    val javaAccessor: String by lazy {
        when {
            // If there's an explicit accessor method, use it
            accessorMethod != null -> "${accessorMethod.simpleName.asString()}()"
            // For data classes, use Java-style getter (getValue())
            element.modifiers.contains(Modifier.DATA) -> "get${wrappedProperty.name.replaceFirstChar { it.uppercase() }}()"
            // For regular classes, check if it's a custom method or use Java getter style
            else -> {
                val propertyName = wrappedProperty.name
                if (hasCustomAccessorMethod()) {
                    "${propertyName}()"
                } else {
                    "get${propertyName.replaceFirstChar { it.uppercase() }}()"
                }
            }
        }
    }

    private fun hasCustomAccessorMethod(): Boolean {
        // Check if there's a method with the same name as the property
        return element.getAllFunctions().any { function ->
            function.simpleName.asString() == wrappedProperty.name &&
                    function.parameters.isEmpty() &&
                    function.returnType?.resolve() == wrappedProperty.type
        }
    }

    fun objectCreation(parameterName: String): String {
        return if (factoryMethod != null) {
            "${typeName}.${factoryMethod.simpleName.asString()}(${parameterName})"
        } else {
            "${typeName}(${parameterName})"
        }
    }
}


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