package com.qualityminds.lazyval.processor;

import com.qualityminds.lazyval.processor.spi.ObjectElement;
import com.qualityminds.lazyval.processor.spi.RecordElement;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class LazyvalEnvironment {

    static final String DISABLED_GENERATORS = "lazyval.disabledGenerators";
    static final String CONFIGURED_VALUES = "lazyval.values";
    private final ProcessingEnvironment processingEnvironment;

    private static final String NO_GENERATION_WARNING = "None of the required classes are available on the classpath! Lazyval will not generate any sources.";
    private static final String NOT_FINAL_OBJECT_WARNING = "Value Types should not be extendable, hence the class should be final.";
    private static final String NOT_FINAL_VALUE_WARNING = "Value Types should be immutable, hence the wrapped field should be final.";

    LazyvalEnvironment(ProcessingEnvironment processingEnvironment) {
        Objects.requireNonNull(processingEnvironment);
        this.processingEnvironment = processingEnvironment;
    }

    public void info(String message) {
        Objects.requireNonNull(message);
        processingEnvironment.getMessager().printMessage(javax.tools.Diagnostic.Kind.NOTE, message);
    }

    void warn(String message) {
        Objects.requireNonNull(message);
        processingEnvironment.getMessager().printMessage(Diagnostic.Kind.MANDATORY_WARNING, message);
    }

    void warn(Element element, String message) {
        Objects.requireNonNull(element);
        Objects.requireNonNull(message);
        processingEnvironment.getMessager().printMessage(Diagnostic.Kind.MANDATORY_WARNING, message, element);
    }

    void warnMissingClasspath(){
        warn(NO_GENERATION_WARNING);
    }

    void error(String message) {
        Objects.requireNonNull(message);
        processingEnvironment.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
    }

    void error(Element element, String message) {
        Objects.requireNonNull(element);
        Objects.requireNonNull(message);
        processingEnvironment.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    /**
     * Checks whether a class with the given fqn is available on the classpath.
     */
    public boolean isClassAvailable(String fqn){
        if (fqn == null || fqn.trim().isEmpty()) {
            warn(fqn + " is not a valid fully qualified class name.");
            return false;
        }
        return processingEnvironment.getElementUtils().getTypeElement(fqn) != null;
    }

    public List<TypeElement> getConfiguredValues(){
        return Arrays.stream(processingEnvironment.getOptions()
                .getOrDefault(LazyvalEnvironment.CONFIGURED_VALUES, "")
                .split(",")).map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(fqn -> {
                    var type = processingEnvironment.getElementUtils().getTypeElement(fqn);
                    if(type == null){
                        error(String.format("Configured value '%s' could not be resolved.", fqn));
                    }
                    return type;
                }).toList();
    }

    public List<String> getDisabledGenerators(){
        return Arrays.stream(processingEnvironment.getOptions()
                        .getOrDefault(LazyvalEnvironment.DISABLED_GENERATORS, "")
                        .split(",")).map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }


    /**
     * While annotation processing, no classes can be loaded (since they are not yet compiled).
     * For annotations whose methods return a class, a TypeMirror is needed.
     * Interestingly enough, the TypeMirror returned by the MirroredTypeException is just what is needed to be used in
     * JavaPoet.
     *
     * @param classValue access to a class from the current compilation unit.
     * @return TypeMirror from the class.
     */
    // Currently not needed but kept for future reference.
    static TypeMirror mirror(Supplier<Class<?>> classValue){
        try {
            var ignored = classValue.get();
            throw new IllegalStateException("Expected a MirroredTypeException to be thrown but got " + ignored);
        }catch (MirroredTypeException e){
            return e.getTypeMirror();
        }
    }


    Optional<ValidatedGeneratorElement> validateElement(TypeElement element){
        var result = validateRecord(element);
        if(result.isPresent()){
            return result;
        }else{
            return validateObject(element);
        }
    }

    private Optional<ValidatedGeneratorElement> validateRecord(TypeElement lazyvalElement){
        if(lazyvalElement.getKind() != ElementKind.RECORD){
            return Optional.empty();
        }
        boolean valid = true;

        var fields = lazyvalElement.getRecordComponents();
        if(fields.size() > 1){
            error(lazyvalElement, "Not a simple ValueType. Lazyval only supports Records with one non-transient field.");
            valid = false;
        }

        var factoryMethods = findFactoryMethods(lazyvalElement, fields.get(0).asType());
        if(factoryMethods.size() > 1){
            error(lazyvalElement, "Multiple matching factory methods with the same signature found. Please check methods:" + factoryMethods.stream().map(ExecutableElement::getSimpleName).collect(Collectors.joining(", ")));
            valid = false;
        }
        Optional<ExecutableElement> factoryMethod = factoryMethods.isEmpty() ? Optional.empty() : Optional.of(factoryMethods.get(0));

        return valid ? Optional.of(new RecordElement(lazyvalElement, fields.get(0), factoryMethod)) : Optional.empty();
    }

    private List<VariableElement> findAccessor(TypeElement lazyvalElement){
        var typeUtils = processingEnvironment.getTypeUtils();
        var accessors = lazyvalElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(element -> (ExecutableElement) element)
                .filter(method ->
                        method.getModifiers().contains(Modifier.PUBLIC)
                                && method.getParameters().isEmpty()
                                && method.getReturnType().getKind() != TypeKind.VOID
                                && !method.getModifiers().contains(Modifier.STATIC))
                .toList();

        return lazyvalElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.FIELD)
                .map(element -> (VariableElement) element)
                .filter(field ->
                        accessors.stream().anyMatch(accessor -> typeUtils.isSameType(accessor.getReturnType(), field.asType()))
                                && !field.getModifiers().contains(Modifier.STATIC)
                                && !field.getModifiers().contains(Modifier.TRANSIENT))
                .toList();
    }

    private List<ExecutableElement> findFactoryMethods(TypeElement lazyvalElement, TypeMirror wrappedType){
        var typeUtils = processingEnvironment.getTypeUtils();
        return lazyvalElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(method -> (ExecutableElement) method)
                .filter(method -> method.getModifiers().contains(Modifier.STATIC)
                        && typeUtils.isSameType(method.getReturnType(), lazyvalElement.asType())
                        && method.getParameters().size() == 1  // Should have exactly one parameter
                        && typeUtils.isSameType(method.getParameters().get(0).asType(), wrappedType))  // Parameter type should match field-type
                .toList();
    }

    private Optional<ValidatedGeneratorElement> validateObject(TypeElement lazyvalElement){
        if(lazyvalElement.getKind() != ElementKind.CLASS){
            return Optional.empty();
        }

        boolean valid = true;

        if (lazyvalElement.getModifiers().contains(Modifier.ABSTRACT)) {
            error(lazyvalElement, "Abstract class is not a valid ValueType.");
            valid = false;
        }

        var valueFields = findAccessor(lazyvalElement);
        if(valueFields.size() > 1){
            error(lazyvalElement, "Not a simple ValueType. Lazyval only supports Objects with one non-transient value.");
            valid = false;
        }else if(valueFields.isEmpty()){
            // FIXME find a way not to stop validation here. Instead of passing accessors, use the field
            error(lazyvalElement,"No public accessor found. Lazyval requires the ValueType to have one accessor. Stopping further validation.");
            return Optional.empty(); // we have to stop here because we need the value field to look up the factory method
        }

        var factoryMethods = findFactoryMethods(lazyvalElement, valueFields.get(0).asType());
        if(factoryMethods.size() > 1){
            error(lazyvalElement, "Multiple matching factory methods with the same signature found. Please check methods:" + factoryMethods.stream().map(ExecutableElement::getSimpleName).collect(Collectors.joining(", ")));
            valid = false;
        }
        Optional<ExecutableElement> factoryMethod = factoryMethods.isEmpty() ? Optional.empty() : Optional.of(factoryMethods.get(0));

        // run warning checks only when the definition is valid in general
        if(valid){
            if(!lazyvalElement.getModifiers().contains(Modifier.FINAL)){
                warn(lazyvalElement, NOT_FINAL_OBJECT_WARNING);
            }
            if(!valueFields.get(0).getModifiers().contains(Modifier.FINAL)){
                warn(valueFields.get(0), NOT_FINAL_VALUE_WARNING);
            }
        }

        return valid ? Optional.of(new ObjectElement(lazyvalElement, valueFields.get(0), factoryMethod)) : Optional.empty();
    }
}
