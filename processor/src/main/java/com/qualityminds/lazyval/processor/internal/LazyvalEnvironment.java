package com.qualityminds.lazyval.processor.internal;

import com.qualityminds.lazyval.LazyvalConfiguration;
import com.qualityminds.lazyval.processor.spi.Generator;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class LazyvalEnvironment {

    static final String DISABLED_GENERATORS = "lazyval.generators.disable";
    static final String SUPERSEDE_ENABLED = "lazyval.generators.supersede";
    static final String BASE_PACKAGE = "lazyval.generators.basePackage";
    private final ProcessingEnvironment processingEnvironment;

    private static final String NO_GENERATION_WARNING = "None of the required classes are available on the classpath! Lazyval will not generate any sources.";

    LazyvalEnvironment(ProcessingEnvironment processingEnvironment) {
        Objects.requireNonNull(processingEnvironment);
        this.processingEnvironment = processingEnvironment;
    }

    Types typeUtils() {
        return processingEnvironment.getTypeUtils();
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
            public Optional<ClassInspection> inspectClass(String fqcn) {
                if (fqcn == null || fqcn.isBlank()) {
                    return Optional.empty();
                }
                TypeElement element = processingEnvironment.getElementUtils().getTypeElement(fqcn);
                if (element == null) {
                    return Optional.empty();
                }
                return Optional.of(new TypeElementInspection(processingEnvironment, element));
            }

            @Override
            public String generatorPackage(String overridePackageOptionKey, @Nullable String defaultLayer) {
                var config = PackageLookup.DefaultConfig.of(
                        getSetting(BASE_PACKAGE).orElse(null),
                        defaultLayer);

                return PackageLookup.computePackage(config, getSetting(overridePackageOptionKey).orElse(null), () -> {
                    var fallbackPackage = processingEnvironment.getElementUtils()
                            .getPackageOf(fallback.element())
                            .getQualifiedName().toString();
                    warn(String.format("""
                                    Neither configuration for '%s' nor '%s' is set. \
                                    Falling back to package of first element: '%s'""", BASE_PACKAGE, overridePackageOptionKey, fallbackPackage));
                    return fallbackPackage;
                });

            }

            @Override
            public void logInfo(Generator generator, String message) {
                info(" [%s] %s".formatted(generator.generatorId(), message));
            }

            @Override
            public void logWarning(Generator generator, String message) {
                warn(" [%s] %s".formatted(generator.generatorId(), message));
            }

            @Override
            public void logWarning(Generator generator, Element element, String message) {
                warn(element, " [%s] %s".formatted(generator.generatorId(), message));
            }

            @Override
            public void logError(Generator generator, String message) {
                error(" [%s] %s".formatted(generator.generatorId(), message));
            }

            @Override
            public void logError(Generator generator, Element element, String message) {
                error(element, " [%s] %s".formatted(generator.generatorId(), message));
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
     *   <li>Deduplicates types listed more than once and reports a warning so the
     *       user can clean up the configuration; the type is still processed once.</li>
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

        // extract externalTypes classes from Annotation
        List<? extends TypeMirror> mirrors = mirrors(config::externalTypes);

        Set<TypeElement> localTypes = roundEnv.getRootElements().stream()
                .filter(e -> e instanceof TypeElement)
                .map(e -> (TypeElement) e)
                .collect(Collectors.toCollection(HashSet::new));

        List<TypeElement> result = new ArrayList<>(mirrors.size());
        Set<String> seenQualifiedNames = new HashSet<>(mirrors.size());
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
            if (!seenQualifiedNames.add(typeElement.getQualifiedName().toString())) {
                warn(holder, String.format(
                        "Duplicate type '%s' in @LazyvalConfiguration.externalTypes. It will only be processed once.",
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

    public boolean isSupersedeEnabled(){
        return Optional.ofNullable(processingEnvironment.getOptions()
                .get(LazyvalEnvironment.SUPERSEDE_ENABLED))
                .map(Boolean::parseBoolean)
                .orElse(true);
    }


    /**
     * While annotation processing, no classes can be loaded (since they are not yet compiled).
     * For annotations whose methods return an array of classes ({@code Class<?>[]}),
     * the compiler throws a {@link MirroredTypesException} instead of returning the array,
     * carrying the corresponding {@link TypeMirror}s — exactly what is needed for tools like JavaPoet.
     *
     * @param classValues access to a class-array attribute from the current compilation unit.
     * @return the list of {@link TypeMirror}s for the classes in the array.
     */
    static List<? extends TypeMirror> mirrors(Supplier<Class<?>[]> classValues){
        try {
            var ignored = classValues.get();
            throw new IllegalStateException("Expected a MirroredTypesException to be thrown but got " + Arrays.toString(ignored));
        } catch (MirroredTypesException e) {
            return e.getTypeMirrors();
        }
    }

    private record TypeElementInspection(ProcessingEnvironment env, TypeElement element) implements Generator.Context.ClassInspection {

        @Override
        public boolean isAccessibleFrom(String packageName) {
            return hasMatchingVisibility(element.getModifiers(), packageName);
        }

        @Override
        public boolean hasAccessibleNoArgConstructor(String packageName) {
            for (Element enclosed : element.getEnclosedElements()) {
                if (enclosed.getKind() != ElementKind.CONSTRUCTOR) continue;
                ExecutableElement ctor = (ExecutableElement) enclosed;
                if (!ctor.getParameters().isEmpty()) continue;
                if (hasMatchingVisibility(ctor.getModifiers(), packageName)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isAssignableTo(String supertypeFqn) {
            TypeElement supertype = env.getElementUtils().getTypeElement(supertypeFqn);
            if (supertype == null) {
                return false;
            }
            Types types = env.getTypeUtils();
            return types.isAssignable(types.erasure(element.asType()), types.erasure(supertype.asType()));
        }

        @Override
        public boolean hasAnnotation(String annotationFqn) {
            for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
                TypeElement annoElement = (TypeElement) mirror.getAnnotationType().asElement();
                if (annoElement.getQualifiedName().contentEquals(annotationFqn)) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasMatchingVisibility(Set<Modifier> modifiers, String packageName) {
            if (modifiers.contains(Modifier.PUBLIC)) return true;
            if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.PROTECTED)) return false;
            String classPkg = env.getElementUtils().getPackageOf(element).getQualifiedName().toString();
            return classPkg.equals(packageName);
        }
    }
}
