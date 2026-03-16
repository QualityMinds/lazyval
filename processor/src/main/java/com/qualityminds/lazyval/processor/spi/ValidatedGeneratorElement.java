package com.qualityminds.lazyval.processor.spi;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;

/**
 * Information about a class that is annotated with @LazyVal suitable for generators to consume.
 * Low-Level access to the <code>java.lang.model.*</code> types is still possible in case generators need further
 * analysis.
 */
@ApiStatus.Experimental
public final class ValidatedGeneratorElement {

    private final TypeElement element;
    private final WrappedType wrappedType;
    private final @Nullable ExecutableElement factoryMethod;
    private final TypeName typeName;
    private final String accessorFragment;

    private ValidatedGeneratorElement(TypeElement element, WrappedType wrappedType, @Nullable ExecutableElement factoryMethod, String accessorFragment){
        this.element = element;
        this.wrappedType = wrappedType;
        this.factoryMethod = factoryMethod;
        this.accessorFragment = accessorFragment;
        typeName = element.asType().accept(new TypeNameVisitor(), null);
    }

    /**
     * Creates a new validated element from the processed information of a regular class.
     * @param element the annotated element.
     * @param factoryMethod the factory method used to create the instance, if any.
     * @param field the field holding the wrapped value.
     * @param accessorMethod the resolved public accessor method for the wrapped field.
     * @return a new GeneratorElement, which is passed to each generator.
     */
    @ApiStatus.Internal
    public static ValidatedGeneratorElement fromClass(TypeElement element, @Nullable ExecutableElement factoryMethod, VariableElement field, ExecutableElement accessorMethod){
        var wrappedType = WrappedType.from(field.asType(), field.getSimpleName().toString());
        var accessorFragment = String.format("%s()", accessorMethod.getSimpleName());
        return new ValidatedGeneratorElement(element, wrappedType, factoryMethod, accessorFragment);
    }

    /**
     * Creates a new validated element from the processed information of a record.
     * @param element the annotated element.
     * @param factoryMethod the factory method used to create the instance, if any.
     * @param field the field holding the wrapped value.
     * @return a new GeneratorElement, which is passed to each generator.
     */
    @ApiStatus.Internal
    public static ValidatedGeneratorElement fromRecord(TypeElement element, @Nullable ExecutableElement factoryMethod, RecordComponentElement field){
        var wrappedType = WrappedType.from(field.asType(), field.getSimpleName().toString());
        // Records always have an accessor named after the component
        var accessorFragment = String.format("%s()", field.getSimpleName());
        return new ValidatedGeneratorElement(element, wrappedType, factoryMethod, accessorFragment);
    }

    /**
     * The simple name of the annotated type.
     * @return name of the type
     */
    public TypeName typeName(){
        return typeName;
    }

    /**
     * Whether the annotated element is a record or not.
     * @return true if record, false otherwise
     */
    public boolean isRecord(){
        return element.getKind() == ElementKind.RECORD;
    }

    /**
     * Returns the low-level {@link TypeElement} annotated as LazyValue or configured by <code>lazyval.values</code>.
     * Can be used to further analyze the code structure.
     * @return the annotated/configured element.
     */
    public TypeElement element() {
        return element;
    }

    /**
     * Return the element that holds the factory method.
     * @return factory method, if any.
     */
    public @Nullable ExecutableElement factoryMethod() {
        return factoryMethod;
    }

    /**
     * Information about the type which is wrapped in a domain-primitive.
     * @return information about the wrapped type.
     */
    public WrappedType wrappedType(){
        return wrappedType;
    }

    /**
     * Creates the generator-string for the accessor-method of the backing-field.
     * @return code-fragment for the accessor-method.
     */
    public String accessor(){
        return accessorFragment;
    }

    /**
     * Creates the generator-string required to recreate the instance.
     * @param parameterName the name of the parameter used in the surrounding scope
     * @return code-fragment to recreate the instance.
     */
    public String objectCreation(String parameterName){
        if(factoryMethod != null){
            return String.format("%s.%s(%s)",
                    typeName(),
                    factoryMethod.getSimpleName(),
                    parameterName);
        }
        // no factory-method, use constructor
        return String.format("new %s(%s)",
                typeName(),
                parameterName);
    }

    /**
     * Information about the wrapped type.
     * @param typeMirror low-level access to the processor-apis TypeMirror
     * @param typeName the name of the type (not-qualified)
     * @param fieldName the name of the field holding the value.
     */
    public record WrappedType(TypeMirror typeMirror, TypeName typeName, String fieldName){

        static WrappedType from(TypeMirror typeMirror, String fieldName){
            @SuppressWarnings("RedundantCast") // needed due to NullMarked
            TypeName wrappedTypeName = typeMirror.accept(new TypeNameVisitor(), (Void)null);
            return new WrappedType(typeMirror, wrappedTypeName, fieldName);
        }

        /**
         * Whether the typeMirror wrapped is a primitive or not, which can require additional boxing in certain generator
         * cases.
         * @return true when primitive, false otherwise
         */
        public boolean isPrimitive(){
            return typeMirror.getKind().isPrimitive();
        }

        /**
         * Convenience method to return the TypeName in the upper-case if necessary.
         * Only needed for primitive types.
         * @return upper-cased TypeName
         */
        public TypeName typeNameUpper(){
            if(isPrimitive()){
                var str = this.typeName.toString();
                return new TypeName(Character.toUpperCase(str.charAt(0)) + str.substring(1));
            }else{
                return this.typeName;
            }
        }
    }
}
