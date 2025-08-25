package de.qualityminds.lazyval.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier

interface ValidatedKspGeneratorElement {
    val element: KSClassDeclaration
    val factoryMethod: KSFunctionDeclaration?
    val wrappedType: KSType
    val wrappedTypeName: String

    /**
     * For Kotlin sources, this will yield the types accessor (being it a property or a function)
     */
    val kotlinAccessorMethodName: String

    /**
     * In case Java sources need to be generated, this will yield the accessors' getter.
     */
    val javaAccessorMethodName: String
}

internal data class KspClassElement(
    override val element: KSClassDeclaration,
    val property: KSPropertyDeclaration,
    val accessorMethod: KSFunctionDeclaration?,
    override val factoryMethod: KSFunctionDeclaration?
) : ValidatedKspGeneratorElement {

    override val wrappedType: KSType = property.type.resolve()
    override val wrappedTypeName: String = property.simpleName.asString()

    override val kotlinAccessorMethodName: String = when {
        // If there's an explicit accessor method, use it (make sure to call as a function with '()')
        accessorMethod != null -> "${accessorMethod.simpleName.asString()}()"
        // For data classes, properties are automatically accessible
        element.modifiers.contains(Modifier.DATA) -> property.simpleName.asString()
        // For regular classes, assume properties are accessible (make sure to call as a property without '()')
        else -> "${property.simpleName.asString()}"
    }

    override val javaAccessorMethodName: String = when {
        // If there's an explicit accessor method, use it
        accessorMethod != null -> "${accessorMethod.simpleName.asString()}()"
        // For data classes, use Java-style getter (getValue())
        element.modifiers.contains(Modifier.DATA) -> "get${property.simpleName.asString().replaceFirstChar { it.uppercase() }}()"
        // For regular classes, check if it's a custom method or use Java getter style
        else -> {
            val propertyName = property.simpleName.asString()
            if (hasCustomAccessorMethod()) {
                "${propertyName}()"
            } else {
                "get${propertyName.replaceFirstChar { it.uppercase() }}()"
            }
        }
    }

    private fun hasCustomAccessorMethod(): Boolean {
        // Check if there's a method with the same name as the property
        return element.getAllFunctions().any { function ->
            function.simpleName.asString() == property.simpleName.asString() &&
                    function.parameters.isEmpty() &&
                    function.returnType?.resolve() == wrappedType
        }
    }
}