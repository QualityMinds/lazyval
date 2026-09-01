package com.qualityminds.lazyval.processor.internal;

import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.*;
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
 * <p>
 * Only the rules live here; pairing a field with its accessor is delegated to {@link AccessorLookup},
 * and the wording of every diagnostic to {@link LazyvalElementValidatorMessages}, because that wording answers to
 * {@code ApIT} and to the KSP validator rather than to the rules. Mirrors the same three-way split on
 * the KSP side.
 */
class LazyvalElementValidator {

    private static final String NOT_FINAL_OBJECT_WARNING =
            "Value Types should not be extendable, hence the class should be final.";
    private static final String NOT_FINAL_VALUE_WARNING =
            "Value Types should be immutable, hence the wrapped field should be final.";
    private static final Set<String> TRANSIENT_ANNOTATIONS = Set.of(
            "jakarta.persistence.Transient",
            "org.springframework.data.annotation.Transient");

    private final Types typeUtils;
    private final LazyvalEnvironment environment;

    LazyvalElementValidator(Types typeUtils, LazyvalEnvironment environment) {
        this.typeUtils = Objects.requireNonNull(typeUtils);
        this.environment = Objects.requireNonNull(environment);
    }

    Optional<ValidatedGeneratorElement> validate(TypeElement element) {
        boolean typeReachable = validateTypeVisibility(element);
        var result = validateRecord(element);
        if (result.isEmpty()) {
            result = validateObject(element);
        }
        // Evaluated alongside the shape rules rather than short-circuiting them, so that a type which
        // is both unreachable and malformed reports everything in one run.
        return typeReachable ? result : Optional.empty();
    }

    /**
     * Generated code has to name the domain-primitive before it can read or rebuild it, and it is
     * emitted into another package — so a type that is not public is out of reach however reachable
     * its members are. Records inherit this on their canonical constructor, which is why
     * {@link #validateReconstruction} defers to this rule rather than reporting there.
     */
    private boolean validateTypeVisibility(TypeElement lazyvalElement) {
        if (lazyvalElement.getModifiers().contains(Modifier.PUBLIC)) {
            return true;
        }
        environment.error(lazyvalElement, LazyvalElementValidatorMessages.nonPublicTypeMessage(lazyvalElement));
        return false;
    }

    /**
     * Every rule is evaluated before the first failure is acted on, so an invalid record reports all
     * of its problems in one compiler run instead of one per fix-and-recompile cycle. The missing
     * component is the sole exception: without it there is nothing to look a factory method up by.
     */
    private Optional<ValidatedGeneratorElement> validateRecord(TypeElement lazyvalElement) {
        if (lazyvalElement.getKind() != ElementKind.RECORD) {
            return Optional.empty();
        }
        var components = lazyvalElement.getRecordComponents();
        if (components.isEmpty()) {
            environment.error(lazyvalElement, "No record component found. Lazyval requires the ValueType to have exactly one field.");
            return Optional.empty();
        }
        var payloadComponents = components.stream()
                .filter(component -> !isTransientComponent(lazyvalElement, component))
                .toList();
        if (payloadComponents.isEmpty()) {
            environment.error(lazyvalElement, "No non-transient record component found. Lazyval requires the"
                    + " ValueType to have exactly one non-transient record component.");
            return Optional.empty();
        }
        boolean payloadValid = payloadComponents.size() <= 1;
        if (!payloadValid) {
            environment.error(lazyvalElement, "Not a simple ValueType. Lazyval only supports Records with one non-transient field.");
        }

        var payloadType = payloadComponents.get(0).asType();
        var factoryMethods = findFactoryMethods(lazyvalElement, payloadType);
        boolean factoryValid = validateFactoryMethods(lazyvalElement, factoryMethods);
        var transientComponents = components.stream()
                .filter(component -> isTransientComponent(lazyvalElement, component))
                .toList();
        // Only answerable once the payload is: with several candidates there is no single type to match
        // a constructor against, and the first component is a guess that would misname the fix.
        boolean reconstructionValid = !payloadValid
                || validateReconstruction(lazyvalElement, payloadType, factoryMethods, transientComponents);

        if (!(payloadValid && factoryValid && reconstructionValid)) {
            return Optional.empty();
        }
        ExecutableElement factoryMethod = factoryMethods.isEmpty() ? null : factoryMethods.get(0);
        return Optional.of(ValidatedGeneratorElement.fromRecord(lazyvalElement, factoryMethod, payloadComponents.get(0)));
    }

    /**
     * A record component counts as transient when the annotation reached any of its mandated
     * members. Neither supported annotation targets RECORD_COMPONENT, so javac propagates a
     * component annotation onto the generated field and accessor (JLS 8.10.3) — those are the
     * elements that have to be consulted; the component element itself is checked for the day an
     * annotation does declare that target.
     */
    private boolean isTransientComponent(TypeElement recordElement, RecordComponentElement component) {
        if (isTransientAnnotated(component) || isTransientAnnotated(component.getAccessor())) {
            return true;
        }
        return instanceFields(recordElement).stream()
                .filter(field -> field.getSimpleName().contentEquals(component.getSimpleName()))
                .anyMatch(this::isTransientAnnotated);
    }

    /**
     * Every rule is evaluated before the first failure is acted on, so an invalid class reports all
     * of its problems in one compiler run instead of one per fix-and-recompile cycle. The missing
     * accessor is the sole exception: without it there is nothing to look a factory method up by.
     */
    private Optional<ValidatedGeneratorElement> validateObject(TypeElement lazyvalElement) {
        if (lazyvalElement.getKind() != ElementKind.CLASS) {
            return Optional.empty();
        }
        boolean shapeValid = !lazyvalElement.getModifiers().contains(Modifier.ABSTRACT);
        if (!shapeValid) {
            environment.error(lazyvalElement, "Abstract class is not a valid ValueType.");
        }

        var fieldAccessorPairs = findFieldAccessorPairs(lazyvalElement);
        if (fieldAccessorPairs.isEmpty()) {
            reportUnreachablePayload(lazyvalElement);
            return Optional.empty();
        }
        boolean payloadValid = fieldAccessorPairs.size() <= 1;
        if (!payloadValid) {
            environment.error(lazyvalElement, "Not a simple ValueType. Lazyval only supports Objects with one non-transient value.");
        }
        var pair = fieldAccessorPairs.get(0);

        var payloadType = pair.field().asType();
        var factoryMethods = findFactoryMethods(lazyvalElement, payloadType);
        boolean factoryValid = validateFactoryMethods(lazyvalElement, factoryMethods);
        // See the note in validateRecord: an ambiguous payload leaves nothing to match against.
        boolean reconstructionValid = !payloadValid
                || validateReconstruction(lazyvalElement, payloadType, factoryMethods, List.of());

        if (!(shapeValid && payloadValid && factoryValid && reconstructionValid)) {
            return Optional.empty();
        }
        warnOnNonFinal(lazyvalElement, pair.field());
        ExecutableElement factoryMethod = factoryMethods.isEmpty() ? null : factoryMethods.get(0);
        return Optional.of(ValidatedGeneratorElement.fromClass(lazyvalElement, factoryMethod, pair.field(), pair.accessor()));
    }

    /**
     * Three mistakes look alike from the outside — a class with no state, a field nobody exposes, and
     * a field exposed only by a non-public method — yet each calls for different advice, and the last
     * two belong on the element the author has to change rather than on the class: someone looking
     * straight at their field is not helped by being told that nothing was found.
     * <p>
     * Validation still stops after this, because without an accessor there is no payload type to look
     * a factory method up by. Reading the payload off the field instead would lift that restriction.
     */
    private void reportUnreachablePayload(TypeElement lazyvalElement) {
        var fields = instanceFields(lazyvalElement).stream()
                .filter(field -> !isTransientField(field))
                .toList();
        if (fields.isEmpty()) {
            environment.error(lazyvalElement, "No non-transient field found. Lazyval requires the ValueType"
                    + " to have exactly one non-transient field exposed by a public accessor.");
            return;
        }
        var allMethods = declaredMethods(lazyvalElement);
        fields.forEach(field -> AccessorLookup.findNonPublicAccessor(field, allMethods).ifPresentOrElse(
                accessor -> environment.error(accessor, LazyvalElementValidatorMessages.nonPublicAccessorMessage(field, accessor)),
                () -> environment.error(field, LazyvalElementValidatorMessages.missingAccessorMessage(field))));
    }

    /**
     * At most one factory method may match the wrapped type; with several, Lazyval cannot tell which
     * one is meant to reconstruct the value. Having none is fine — the constructor is then used.
     */
    private boolean validateFactoryMethods(TypeElement lazyvalElement, List<ExecutableElement> factoryMethods) {
        if (factoryMethods.size() <= 1) {
            return true;
        }
        environment.error(lazyvalElement, "Multiple matching factory methods with the same signature found. Please check methods:"
                + factoryMethods.stream().map(m -> m.getSimpleName().toString()).collect(Collectors.joining(", ")));
        return false;
    }

    /**
     * Reading the payload is only half the contract — the value also has to be reconstructible from
     * it. Generated code calls either a factory method or a constructor, both from another package,
     * so a constructor it cannot reach is no better than one that does not exist. A factory settles
     * the question on its own; only in its absence does the constructor have to carry the weight.
     * <p>
     * Left unchecked, either mistake surfaces as a compiler error inside generated sources, which is
     * exactly what Lazyval promises never to emit.
     */
    private boolean validateReconstruction(TypeElement lazyvalElement,
                                           TypeMirror payloadType,
                                           List<ExecutableElement> factoryMethods,
                                           List<? extends Element> transientComponents) {
        if (factoryMethods.size() > 1) {
            // Ambiguity is already reported by validateFactoryMethods, and until it is resolved there is
            // no single factory whose reachability could be judged.
            return true;
        }
        if (factoryMethods.size() == 1) {
            var factory = factoryMethods.get(0);
            if (!factory.getModifiers().contains(Modifier.PUBLIC)) {
                environment.error(factory, LazyvalElementValidatorMessages.nonPublicFactoryMessage(factory));
                return false;
            }
            return true;
        }
        var constructor = findPayloadConstructor(lazyvalElement, payloadType);
        if (constructor.isEmpty()) {
            environment.error(lazyvalElement,
                    LazyvalElementValidatorMessages.missingReconstructionMessage(lazyvalElement, payloadType, transientComponents));
            return false;
        }
        // A non-public type is already out of reach as a whole, and a record's canonical constructor
        // simply inherits that visibility — pointing at the constructor would send the author to fix
        // the wrong declaration. validateTypeVisibility owns that diagnostic and has already made it.
        if (!lazyvalElement.getModifiers().contains(Modifier.PUBLIC)) {
            return true;
        }
        if (!constructor.get().getModifiers().contains(Modifier.PUBLIC)) {
            environment.error(constructor.get(),
                    LazyvalElementValidatorMessages.nonPublicConstructorMessage(lazyvalElement, constructor.get()));
            return false;
        }
        return true;
    }

    private Optional<ExecutableElement> findPayloadConstructor(TypeElement lazyvalElement, TypeMirror payloadType) {
        return lazyvalElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.CONSTRUCTOR)
                .map(element -> (ExecutableElement) element)
                .filter(constructor -> constructor.getParameters().size() == 1
                        && typeUtils.isSameType(constructor.getParameters().get(0).asType(), payloadType))
                .findFirst();
    }

    /**
     * Advice rather than a rule, and therefore only emitted once the type is known to be valid: a
     * class that is being rejected should not also be lectured about style.
     */
    private void warnOnNonFinal(TypeElement lazyvalElement, VariableElement valueField) {
        if (!lazyvalElement.getModifiers().contains(Modifier.FINAL)) {
            environment.warn(lazyvalElement, NOT_FINAL_OBJECT_WARNING);
        }
        if (!valueField.getModifiers().contains(Modifier.FINAL)) {
            environment.warn(valueField, NOT_FINAL_VALUE_WARNING);
        }
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
     * Finds fields paired with their public accessor methods. APT-side element discovery happens
     * here; the actual pairing — including all candidate filtering — is delegated to
     * {@link AccessorLookup#findAccessor(VariableElement, List)}.
     */
    private List<FieldAccessorPair> findFieldAccessorPairs(TypeElement lazyvalElement) {
        var allMethods = declaredMethods(lazyvalElement);

        return instanceFields(lazyvalElement).stream()
                .flatMap(field -> AccessorLookup.findAccessor(field, allMethods)
                        .map(accessor -> new FieldAccessorPair(field, accessor))
                        .stream())
                .filter(pair -> !isTransient(pair))
                .toList();
    }

    private static List<ExecutableElement> declaredMethods(TypeElement lazyvalElement) {
        return lazyvalElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(element -> (ExecutableElement) element)
                .toList();
    }

    /** Static fields hold no per-instance payload, so they are none of Lazyval's business. */
    private static List<VariableElement> instanceFields(TypeElement lazyvalElement) {
        return lazyvalElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.FIELD)
                .map(element -> (VariableElement) element)
                .filter(field -> !field.getModifiers().contains(Modifier.STATIC))
                .toList();
    }

    /**
     * A value counts as transient when either half of the pair says so. Both placements occur in
     * practice: with field access the annotation sits on the field, with property access it sits on
     * the accessor — which is why this runs after the pairing rather than before it.
     */
    private boolean isTransient(FieldAccessorPair pair) {
        return isTransientField(pair.field()) || isTransientAnnotated(pair.accessor());
    }

    /**
     * The half of {@link #isTransient} that can be answered without an accessor, which is what the
     * diagnostics need: a field excluded by the author is not a field that is missing its accessor.
     */
    private boolean isTransientField(VariableElement field) {
        return field.getModifiers().contains(Modifier.TRANSIENT) || isTransientAnnotated(field);
    }

    private boolean isTransientAnnotated(Element element) {
        return element.getAnnotationMirrors().stream()
                .map(annotation -> annotation.getAnnotationType().toString())
                .anyMatch(TRANSIENT_ANNOTATIONS::contains);
    }

    private record FieldAccessorPair(VariableElement field, ExecutableElement accessor) {}
}
