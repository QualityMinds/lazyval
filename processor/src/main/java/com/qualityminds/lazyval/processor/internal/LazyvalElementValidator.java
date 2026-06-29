package com.qualityminds.lazyval.processor.internal;

import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates a {@link TypeElement} against Lazyval's value-type contract and returns a
 * {@link ValidatedGeneratorElement} ready for code generation. Errors and warnings are
 * reported via the supplied {@link LazyvalEnvironment}.
 */
class LazyvalElementValidator {

    private static final String NOT_FINAL_OBJECT_WARNING =
            "Value Types should not be extendable, hence the class should be final.";
    private static final String NOT_FINAL_VALUE_WARNING =
            "Value Types should be immutable, hence the wrapped field should be final.";
    // Excluded when scanning for accessor candidates: every class inherits/overrides these from Object
    // and `hashCode(): int` / `toString(): String` will type-collide with common wrapped types,
    // causing the first-match logic in findFieldAccessorPairs to pair a field with the wrong getter.
    private static final Set<String> OBJECT_METHOD_NAMES = Set.of("equals", "hashCode", "toString");

    private final Types typeUtils;
    private final LazyvalEnvironment environment;

    LazyvalElementValidator(Types typeUtils, LazyvalEnvironment environment) {
        this.typeUtils = Objects.requireNonNull(typeUtils);
        this.environment = Objects.requireNonNull(environment);
    }

    Optional<ValidatedGeneratorElement> validate(TypeElement element) {
        var result = validateRecord(element);
        if (result.isPresent()) {
            return result;
        }
        return validateObject(element);
    }

    private Optional<ValidatedGeneratorElement> validateRecord(TypeElement lazyvalElement) {
        if (lazyvalElement.getKind() != ElementKind.RECORD) {
            return Optional.empty();
        }
        boolean valid = true;

        var fields = lazyvalElement.getRecordComponents();
        if (fields.size() > 1) {
            environment.error(lazyvalElement, "Not a simple ValueType. Lazyval only supports Records with one non-transient field.");
            valid = false;
        }

        var factoryMethods = findFactoryMethods(lazyvalElement, fields.get(0).asType());
        if (factoryMethods.size() > 1) {
            environment.error(lazyvalElement, "Multiple matching factory methods with the same signature found. Please check methods:"
                    + factoryMethods.stream().map(m -> m.getSimpleName().toString()).collect(Collectors.joining(", ")));
            valid = false;
        }

        ExecutableElement factoryMethod = factoryMethods.isEmpty() ? null : factoryMethods.get(0);
        return valid
                ? Optional.of(ValidatedGeneratorElement.fromRecord(lazyvalElement, factoryMethod, fields.get(0)))
                : Optional.empty();
    }

    private Optional<ValidatedGeneratorElement> validateObject(TypeElement lazyvalElement) {
        if (lazyvalElement.getKind() != ElementKind.CLASS) {
            return Optional.empty();
        }
        boolean valid = true;

        if (lazyvalElement.getModifiers().contains(Modifier.ABSTRACT)) {
            environment.error(lazyvalElement, "Abstract class is not a valid ValueType.");
            valid = false;
        }

        var fieldAccessorPairs = findFieldAccessorPairs(lazyvalElement);
        if (fieldAccessorPairs.size() > 1) {
            environment.error(lazyvalElement, "Not a simple ValueType. Lazyval only supports Objects with one non-transient value.");
            valid = false;
        } else if (fieldAccessorPairs.isEmpty()) {
            // FIXME find a way not to stop validation here. Instead of passing accessors, use the field
            environment.error(lazyvalElement, "No public accessor found. Lazyval requires the ValueType to have one accessor. Stopping further validation.");
            return Optional.empty(); // we have to stop here because we need the value field to look up the factory method
        }

        var pair = fieldAccessorPairs.get(0);
        var factoryMethods = findFactoryMethods(lazyvalElement, pair.field().asType());
        if (factoryMethods.size() > 1) {
            environment.error(lazyvalElement, "Multiple matching factory methods with the same signature found. Please check methods:"
                    + factoryMethods.stream().map(m -> m.getSimpleName().toString()).collect(Collectors.joining(", ")));
            valid = false;
        }
        ExecutableElement factoryMethod = factoryMethods.isEmpty() ? null : factoryMethods.get(0);

        // Warnings only emit when validation otherwise passes.
        if (valid) {
            if (!lazyvalElement.getModifiers().contains(Modifier.FINAL)) {
                environment.warn(lazyvalElement, NOT_FINAL_OBJECT_WARNING);
            }
            if (!pair.field().getModifiers().contains(Modifier.FINAL)) {
                environment.warn(pair.field(), NOT_FINAL_VALUE_WARNING);
            }
        }

        return valid
                ? Optional.of(ValidatedGeneratorElement.fromClass(lazyvalElement, factoryMethod, pair.field(), pair.accessor()))
                : Optional.empty();
    }

    private List<ExecutableElement> findFactoryMethods(TypeElement lazyvalElement, TypeMirror wrappedType) {
        return lazyvalElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(element -> (ExecutableElement) element)
                .filter(method -> method.getModifiers().contains(Modifier.STATIC)
                        && typeUtils.isSameType(method.getReturnType(), lazyvalElement.asType())
                        && method.getParameters().size() == 1
                        && typeUtils.isSameType(method.getParameters().get(0).asType(), wrappedType))
                .toList();
    }

    /**
     * Finds fields paired with their public accessor methods.
     * An accessor is a public, non-static, no-arg method whose return type matches the field type.
     */
    private List<FieldAccessorPair> findFieldAccessorPairs(TypeElement lazyvalElement) {
        var accessors = lazyvalElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(element -> (ExecutableElement) element)
                .filter(method -> method.getModifiers().contains(Modifier.PUBLIC)
                        && method.getParameters().isEmpty()
                        && method.getReturnType().getKind() != TypeKind.VOID
                        && !method.getModifiers().contains(Modifier.STATIC)
                        && !OBJECT_METHOD_NAMES.contains(method.getSimpleName().toString()))
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
