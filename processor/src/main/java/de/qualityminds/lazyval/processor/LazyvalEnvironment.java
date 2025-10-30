package de.qualityminds.lazyval.processor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class LazyvalEnvironment {
    /**
     * Overrides the target package for generated JPA classes.
     */
    static final String JPA_GENERATED_PACKAGE = "lazyval.jpa.generatedPackage";

    /**
     * Overrides the target package for generated Mapstruct classes.
     */
    static final String MAPSTRUCT_GENERATED_PACKAGE = "lazyval.mapstruct.generatedPackage";

    private final UserSettings settings;
    private final ProcessingEnvironment processingEnvironment;

    private final boolean mapstructOnClasspath;
    private final boolean jpaOnClasspath;
    private static final String NO_GENERATION_WARNING = "Neither Mapstruct nor JPA is available on the classpath! Lazyval will not generate any sources.";
    private static final String NOT_FINAL_OBJECT_WARNING = "Value Types should not be extendable, hence the class should be final.";
    private static final String NOT_FINAL_VALUE_WARNING = "Value Types should be immutable, hence the wrapped field should be final.";

    LazyvalEnvironment(ProcessingEnvironment processingEnvironment) {
        Objects.requireNonNull(processingEnvironment);
        this.processingEnvironment = processingEnvironment;
        this.settings = loadSettings();
        this.mapstructOnClasspath = isClassAvailable("org.mapstruct.Mapper");
        this.jpaOnClasspath = isClassAvailable("jakarta.persistence.AttributeConverter");
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

    public boolean isMapstructMissingOnClasspath() {
        return !mapstructOnClasspath;
    }

    boolean isJpaMissingClasspath(){
        return !jpaOnClasspath;
    }

    /**
     * Checks whether a class with the given fqn is available on the classpath.
     */
    public boolean isClassAvailable(String fqn){
        if (fqn == null || fqn.trim().isEmpty()) {
            return false;
        }
        return processingEnvironment.getElementUtils().getTypeElement(fqn) != null;
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

    /**
     * The settings passed via compiler arguments, which have been validated by this point already.
     */
    public UserSettings getSettings() {
        return settings;
    }

    // TODO revisit
    private static final Set<String> layerPackages = Set.of("boundary", "control", "entity", "application", "infrastructure", "domain");
    private static final Predicate<String> IS_NOT_LAYER_PACKAGE_AND_CLASS = part -> !layerPackages.contains(part) && !Character.isUpperCase(part.charAt(0));

    /**
     * Split the FQN into its constituents and returns the substring before the "layer-markers",
     * which will be the root package.
     */
    public static String extractRootPackage(TypeElement element) {
        return Arrays.stream(element.getQualifiedName().toString().split("\\."))
                .takeWhile(IS_NOT_LAYER_PACKAGE_AND_CLASS)
                .collect(Collectors.joining("."));
    }

    private UserSettings loadSettings(){
        var jpaPackage = processingEnvironment.getOptions().get(JPA_GENERATED_PACKAGE);
        var mapstructPackage = processingEnvironment.getOptions().get(MAPSTRUCT_GENERATED_PACKAGE);
        return new UserSettings(jpaPackage, mapstructPackage);
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
            error(lazyvalElement, "Not a simple ValueType. Lazyval only supported Records with one non-transient field name 'value'.");
            valid = false;
        }

        var factoryMethods = findFactoryMethods(lazyvalElement, fields.get(0).asType());
        if(factoryMethods.size() > 1){
            error(lazyvalElement, "Not a simple ValueType. Lazyval only supported Records with one non-transient.");
            valid = false;
        }
        Optional<ExecutableElement> factoryMethod = factoryMethods.isEmpty() ? Optional.empty() : Optional.of(factoryMethods.get(0));

        return valid ? Optional.of(new RecordElement(lazyvalElement, fields.get(0), factoryMethod)) : Optional.empty();
    }

    private static List<VariableElement> findAccessor(TypeElement lazyvalElement){
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
                        accessors.stream().anyMatch(accessor -> accessor.getReturnType().equals(field.asType()))
                        && !field.getModifiers().contains(Modifier.STATIC)
                        && !field.getModifiers().contains(Modifier.TRANSIENT))
                .toList();
    }

    private static List<ExecutableElement> findFactoryMethods(TypeElement lazyvalElement, TypeMirror wrappedType){
        return lazyvalElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(method -> (ExecutableElement) method)
                .filter(method -> method.getModifiers().contains(Modifier.STATIC)
                        && method.getReturnType().equals(lazyvalElement.asType())
                        && method.getParameters().size() == 1  // Should have exactly one parameter
                        && method.getParameters().get(0).asType().toString().equals(wrappedType.toString()))  // Parameter type should match field type)
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
            error(lazyvalElement,"No public accessor found. Lazyval requires the ValueType to have one accessor'. Stopping further validation.");
            return Optional.empty(); // we have to stop here because we need the value field to look up the factory method
        }

        var factoryMethods = findFactoryMethods(lazyvalElement, valueFields.get(0).asType());
        if(factoryMethods.size() > 1){
            error(lazyvalElement, "Not a simple ValueType. Lazyval only supported Records with one non-transient.");
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

    /**
     * Via compiler argument passed configuration.
     * @param jpa see {@link #JPA_GENERATED_PACKAGE}
     */
    public record UserSettings(String jpa, String mapstruct) {
        /**
         * Overrides the default package for the JPA-AttributeConverter, which is used by the annotation processor
         * to write the generated classes.
         * @return Package to which generated classes should be written.
         */
        public Optional<String> getJpaConverterPackage(){
            return Optional.ofNullable(jpa);
        }
        /**
         * Overrides the default package for the Mapstruct mapper, which is used by the annotation processor
         * to write the generated classes.
         * @return Package to which generated classes should be written.
         */
        public Optional<String> getMapstructPackage(){
            return Optional.ofNullable(mapstruct);
        }
    }
}
