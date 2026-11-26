package com.qualityminds.lazyval.processor.internal;

import org.jspecify.annotations.Nullable;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Set;

/**
 * What a type offers as its payload: exactly one member, or the reason there is not exactly one. Found by
 * Lazyval's conventions and judged by {@link LazyvalElementValidator}, which reports every case but
 * {@link Found}.
 *
 * <p>The conventions: a record's payload is a component, a class's a field read through a public accessor, which
 * {@link AccessorLookup} pairs it with. Either is left out when it is declared derived — {@code transient}, or
 * annotated with one of {@link #TRANSIENT_ANNOTATIONS} — and a static field holds no per-instance payload at all.
 */
sealed interface PayloadLookup {

    /**
     * Accepted in place of the {@code transient} keyword, which only carries meaning for a {@code Serializable}
     * type and is flagged by static analysis everywhere else. These two because of their wide use.
     */
    Set<String> TRANSIENT_ANNOTATIONS = Set.of(
            "jakarta.persistence.Transient",
            "org.springframework.data.annotation.Transient");

    /**
     * No member holds a payload.
     *
     * @param transientOnly whether a record declares components, every one of them transient
     */
    record None(boolean transientOnly) implements PayloadLookup {}

    /**
     * A class has fields that would hold a payload, but none of them has a public accessor.
     *
     * @param fields each of those fields
     */
    record Unreadable(List<UnreadableField> fields) implements PayloadLookup {}

    /**
     * A field nobody can read.
     *
     * @param field             the field
     * @param nonPublicAccessor the method that would have exposed it if only it were public, or {@code null} for
     *                          none: the first calls for widening a method, the second for writing one
     */
    record UnreadableField(VariableElement field, @Nullable ExecutableElement nonPublicAccessor) {}

    /**
     * More than one member holds a payload.
     *
     * @param members each of them
     */
    record Several(List<Element> members) implements PayloadLookup {}

    /**
     * The payload.
     *
     * @param declaration         the record component or field, where a finding about the payload is reported
     * @param accessor            the public method generated code reads the payload through
     * @param transientComponents a record's components the canonical constructor takes beside the payload; empty
     *                            for a class
     */
    record Found(Element declaration, ExecutableElement accessor, List<RecordComponentElement> transientComponents)
            implements PayloadLookup {

        /**
         * The type of the payload, as declared.
         *
         * @return the payload type
         */
        TypeMirror type() {
            return declaration.asType();
        }
    }

    /**
     * Looks up the payload of {@code type}.
     *
     * @param type a record or a class
     * @return the payload, or why there is not exactly one
     */
    static PayloadLookup of(TypeElement type) {
        if (type.getKind() == ElementKind.RECORD) {
            List<RecordComponentElement> components = List.copyOf(type.getRecordComponents());
            var payloads = components.stream().filter(component -> !isTransientComponent(type, component)).toList();
            if (payloads.isEmpty()) {
                return new None(!components.isEmpty());
            }
            if (payloads.size() > 1) {
                return new Several(List.copyOf(payloads));
            }
            var transientComponents = components.stream().filter(component -> !payloads.contains(component)).toList();
            return new Found(payloads.get(0), payloads.get(0).getAccessor(), transientComponents);
        }
        var methods = declaredMethods(type);
        // Paired before anything is filtered, because either half may say the value is derived: with field
        // access the annotation sits on the field, with property access on the accessor.
        var payloads = instanceFields(type).stream()
                .flatMap(field -> AccessorLookup.findAccessor(field, methods)
                        .filter(accessor -> !isTransientField(field) && !isTransientAnnotated(accessor))
                        .map(accessor -> new Found(field, accessor, List.of()))
                        .stream())
                .toList();
        if (payloads.size() == 1) {
            return payloads.get(0);
        }
        if (payloads.size() > 1) {
            return new Several(payloads.stream().map(Found::declaration).toList());
        }
        var fields = instanceFields(type).stream().filter(field -> !isTransientField(field)).toList();
        if (fields.isEmpty()) {
            return new None(false);
        }
        return new Unreadable(fields.stream()
                .map(field -> new UnreadableField(field, AccessorLookup.findNonPublicAccessor(field, methods).orElse(null)))
                .toList());
    }

    /**
     * A record component counts as transient when the annotation reached any of its mandated members. Neither
     * supported annotation targets RECORD_COMPONENT, so javac propagates a component annotation onto the generated
     * field and accessor (JLS 8.10.3) — those are the elements that have to be consulted; the component element
     * itself is checked for the day an annotation does declare that target.
     */
    private static boolean isTransientComponent(TypeElement record, RecordComponentElement component) {
        if (isTransientAnnotated(component) || isTransientAnnotated(component.getAccessor())) {
            return true;
        }
        return instanceFields(record).stream()
                .filter(field -> field.getSimpleName().contentEquals(component.getSimpleName()))
                .anyMatch(PayloadLookup::isTransientAnnotated);
    }

    /**
     * Whether the field says it is derived, which is the half of the question that can be answered without an
     * accessor — all a field nothing exposes has to offer.
     */
    private static boolean isTransientField(VariableElement field) {
        return field.getModifiers().contains(Modifier.TRANSIENT) || isTransientAnnotated(field);
    }

    private static boolean isTransientAnnotated(Element element) {
        return element.getAnnotationMirrors().stream()
                .map(annotation -> annotation.getAnnotationType().toString())
                .anyMatch(TRANSIENT_ANNOTATIONS::contains);
    }

    private static List<ExecutableElement> declaredMethods(TypeElement type) {
        return type.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(element -> (ExecutableElement) element)
                .toList();
    }

    /** Static fields hold no per-instance payload, so they are none of Lazyval's business. */
    private static List<VariableElement> instanceFields(TypeElement type) {
        return type.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.FIELD)
                .map(element -> (VariableElement) element)
                .filter(field -> !field.getModifiers().contains(Modifier.STATIC))
                .toList();
    }
}
