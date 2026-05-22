package com.qualityminds.lazyval.processor.internal;

import com.qualityminds.lazyval.LazyvalConfiguration;
import com.qualityminds.lazyval.processor.spi.Generator;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class LazyvalEnvironment {

    static final String DISABLED_GENERATORS = "lazyval.generators.disable";
    static final String BASE_PACKAGE = "lazyval.generators.basePackage";
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
        processingEnvironment.getMessager().printMessage(javax.tools.Diagnostic.Kind.NOTE, "Lazyval: " + message);
    }

    void warn(String message) {
        Objects.requireNonNull(message);
        processingEnvironment.getMessager().printMessage(Diagnostic.Kind.MANDATORY_WARNING, "Lazyval: " + message);
    }

    void warn(Element element, String message) {
        Objects.requireNonNull(element);
        Objects.requireNonNull(message);
        processingEnvironment.getMessager().printMessage(Diagnostic.Kind.MANDATORY_WARNING, "Lazyval: " + message, element);
    }

    void warnMissingClasspath(){
        warn(NO_GENERATION_WARNING);
    }

    void error(String message) {
        Objects.requireNonNull(message);
        processingEnvironment.getMessager().printMessage(Diagnostic.Kind.ERROR, "Lazyval: " + message);
    }

    void error(Element element, String message) {
        Objects.requireNonNull(element);
        Objects.requireNonNull(message);
        processingEnvironment.getMessager().printMessage(Diagnostic.Kind.ERROR, "Lazyval: " + message, element);
    }

    Generator.Context createContext(ValidatedGeneratorElement fallback){
        return new Generator.Context() {

            @Override
            public boolean isOnClasspath(String fqcn) {
                return isClassAvailable(fqcn);
            }

            @Override
            public Optional<String> getSetting(String key) {
                return Optional.ofNullable(processingEnvironment.getOptions().get(key));
            }

            @Override
            public String generatorPackage(String overridePackageOptionKey, @Nullable String defaultLayer) {
                var basePackage = getSetting(BASE_PACKAGE);
                return getSetting(overridePackageOptionKey)
                    .orElseGet(() -> basePackage.map(it -> defaultLayer != null ? it + "." + defaultLayer : it)
                            .orElseGet(() -> {
                                var fallbackPackage = processingEnvironment.getElementUtils()
                                        .getPackageOf(fallback.element())
                                        .getQualifiedName().toString();
                                warn(String.format("""
                                    Neither configuration for '%s' nor '%s' is set. \
                                    Falling back to package of first element: '%s'""", BASE_PACKAGE, overridePackageOptionKey, fallbackPackage));
                                return fallbackPackage;
                            }));
            }
        };
    }

    /**
     * Checks whether a class with the given fqn is available on the classpath.
     */
    public boolean isClassAvailable(String fqn){
        // null-check needed as custom spi might not use jspecify
        //noinspection ConstantValue
        if (fqn == null || fqn.trim().isEmpty()) {
            warn(fqn + " is not a valid fully qualified class name.");
            return false;
        }
        return processingEnvironment.getElementUtils().getTypeElement(fqn) != null;
    }

    /**
     * Reads {@code @LazyvalConfiguration#externalTypes()} from the current round's
     * {@code package-info.java}.
     * <ul>
     *   <li>Returns an empty list when no holder is present.</li>
     *   <li>Reports a compile error and returns an empty list when more than one
     *       holder is present.</li>
     *   <li>Skips and reports a compile error for any listed type that belongs to
     *       the current compilation unit (such types must use {@link com.qualityminds.lazyval.LazyValue}).</li>
     * </ul>
     */
    public List<TypeElement> getConfiguredValues(RoundEnvironment roundEnv) {
        var configAnnotation = processingEnvironment.getElementUtils()
                .getTypeElement(LazyvalConfiguration.class.getCanonicalName());
        if (configAnnotation == null) {
            return List.of();
        }

        var holders = roundEnv.getElementsAnnotatedWith(configAnnotation);
        if (holders.isEmpty()) {
            return List.of();
        }
        if (holders.size() > 1) {
            holders.stream().skip(1).forEach(extra ->
                    error(extra, "Only one @LazyvalConfiguration is allowed per compilation unit."));
            return List.of();
        }

        var holder = holders.iterator().next();
        var config = holder.getAnnotation(LazyvalConfiguration.class);
        if (config == null) {
            return List.of();
        }

        List<? extends TypeMirror> mirrors;
        try {
            config.externalTypes();
            return List.of(); // unreachable — class literals always throw
        } catch (MirroredTypesException e) {
            mirrors = e.getTypeMirrors();
        }

        Set<TypeElement> localTypes = roundEnv.getRootElements().stream()
                .filter(e -> e instanceof TypeElement)
                .map(e -> (TypeElement) e)
                .collect(Collectors.toCollection(HashSet::new));

        List<TypeElement> result = new ArrayList<>(mirrors.size());
        for (TypeMirror mirror : mirrors) {
            var typeElement = (TypeElement) processingEnvironment.getTypeUtils().asElement(mirror);
            if (typeElement == null) {
                continue;
            }
            if (localTypes.contains(typeElement)) {
                error(holder, String.format(
                        "Type '%s' listed in @LazyvalConfiguration.externalTypes belongs to the current compilation unit. Annotate it with @LazyValue directly.",
                        typeElement.getQualifiedName()));
                continue;
            }
            result.add(typeElement);
        }
        return result;
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

        ExecutableElement factoryMethod = factoryMethods.isEmpty() ? null : factoryMethods.get(0);
        return valid ? Optional.of(ValidatedGeneratorElement.fromRecord(lazyvalElement, factoryMethod, fields.get(0))) : Optional.empty();
    }


    private List<ExecutableElement> findFactoryMethods(TypeElement lazyvalElement, TypeMirror wrappedType){
        var typeUtils = processingEnvironment.getTypeUtils();
        return lazyvalElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(method -> (ExecutableElement) method)
                .filter(method -> method.getModifiers().contains(Modifier.STATIC)
                        && typeUtils.isSameType(method.getReturnType(), lazyvalElement.asType())
                        && method.getParameters().size() == 1  // Should have exactly one parameter
                        && typeUtils.isSameType(method.getParameters().get(0).asType(), wrappedType))  // Parameter typeMirror should match field-typeMirror
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

        var fieldAccessorPairs = findFieldAccessorPairs(lazyvalElement);
        if(fieldAccessorPairs.size() > 1){
            error(lazyvalElement, "Not a simple ValueType. Lazyval only supports Objects with one non-transient value.");
            valid = false;
        }else if(fieldAccessorPairs.isEmpty()){
            // FIXME find a way not to stop validation here. Instead of passing accessors, use the field
            error(lazyvalElement,"No public accessor found. Lazyval requires the ValueType to have one accessor. Stopping further validation.");
            return Optional.empty(); // we have to stop here because we need the value field to look up the factory method
        }

        var pair = fieldAccessorPairs.get(0);
        var factoryMethods = findFactoryMethods(lazyvalElement, pair.field().asType());
        if(factoryMethods.size() > 1){
            error(lazyvalElement, "Multiple matching factory methods with the same signature found. Please check methods:" + factoryMethods.stream().map(ExecutableElement::getSimpleName).collect(Collectors.joining(", ")));
            valid = false;
        }
        ExecutableElement factoryMethod = factoryMethods.isEmpty() ? null : factoryMethods.get(0);

        // run warning checks only when the definition is valid in general
        if(valid){
            if(!lazyvalElement.getModifiers().contains(Modifier.FINAL)){
                warn(lazyvalElement, NOT_FINAL_OBJECT_WARNING);
            }
            if(!pair.field().getModifiers().contains(Modifier.FINAL)){
                warn(pair.field(), NOT_FINAL_VALUE_WARNING);
            }
        }

        return valid ? Optional.of(ValidatedGeneratorElement.fromClass(lazyvalElement, factoryMethod, pair.field(), pair.accessor())) : Optional.empty();
    }

    /**
     * Finds fields paired with their public accessor methods.
     * An accessor is a public, non-static, no-arg method whose return type matches the field type.
     */
    private List<FieldAccessorPair> findFieldAccessorPairs(TypeElement lazyvalElement){
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
                .filter(field -> !field.getModifiers().contains(Modifier.STATIC)
                        && !field.getModifiers().contains(Modifier.TRANSIENT))
                .flatMap(field -> accessors.stream()
                        .filter(accessor -> typeUtils.isSameType(accessor.getReturnType(), field.asType()))
                        .findFirst()
                        .map(accessor -> new FieldAccessorPair(field, accessor))
                        .stream())
                .toList();
    }

    private record FieldAccessorPair(VariableElement field, ExecutableElement accessor) {}
}
