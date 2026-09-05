package com.qualityminds.lazyval.processor.internal;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Pure, APT-free heuristic for pairing a value-type's field with the accessor method that exposes
 * it to generated code. The matching rules ({@link #accessorCandidates}, {@link #findAccessor}) are
 * fully data-driven so they can be unit-tested without constructing APT element trees. The boundary
 * adapters ({@link #toShape}, {@link #matchesShape}) live here too so the entire APT-to-heuristic
 * contract is in one place.
 * <p>
 * Intentionally simpler than the KSP-side {@code AccessorLookup}: Java doesn't have the data-class /
 * {@code component1()} hazard that motivated KSP's three-tier ordering. A first-by-type match,
 * combined with filtering out static/non-public/multi-arg/void-returning/Object methods, is enough
 * to pair field-and-getter consistently — both for {@code @LazyValue}-annotated local classes and
 * for external Java types like {@code java.time.Year} where the field name and getter name differ.
 */
final class AccessorLookup {

    private AccessorLookup() {}

    /**
     * Inherited from {@code Object} by every class. {@code hashCode(): int} and
     * {@code toString(): String} collide with common payload types and would otherwise be
     * picked up by the first-by-type match, pairing a field with the wrong getter.
     */
    public static final Set<String> OBJECT_METHOD_NAMES = Set.of("equals", "hashCode", "toString");

    /** APT-free identity of a field: the name as APT exposes it, and the resolved type's FQN. */
    public record Property(String name, String typeFqn) {}

    /**
     * APT-free identity of a candidate method, carrying the structural flags the candidate filter
     * needs. {@code isStatic} and {@code isPublic} are decided at {@link #toShape} time so that
     * platform-specific nuances stay at the APT boundary inside this class.
     */
    public record Method(
            String name,
            String returnTypeFqn,
            int parameterCount,
            boolean isPublic,
            boolean isStatic) {}

    /** Maps an APT field element to its {@link Property} shape. */
    public static Property toProperty(VariableElement field) {
        return new Property(
                field.getSimpleName().toString(),
                field.asType().toString());
    }

    /** Maps an APT method element to its {@link Method} shape. */
    public static Method toShape(ExecutableElement method) {
        var returnType = method.getReturnType().getKind() == TypeKind.VOID
                ? "void"
                : method.getReturnType().toString();
        return new Method(
                method.getSimpleName().toString(),
                returnType,
                method.getParameters().size(),
                method.getModifiers().contains(Modifier.PUBLIC),
                method.getModifiers().contains(Modifier.STATIC));
    }

    /** Whether an APT method element corresponds to the given {@link Method} shape (used for lookback). */
    public static boolean matchesShape(ExecutableElement method, Method shape) {
        return method.getSimpleName().toString().equals(shape.name())
                && method.getReturnType().toString().equals(shape.returnTypeFqn());
    }

    /**
     * Filters {@code methods} to those plausible as a value-type accessor: public, non-static,
     * zero-arg, non-void return, not one of {@link #OBJECT_METHOD_NAMES}.
     */
    public static List<Method> accessorCandidates(List<Method> methods) {
        return candidates(methods, true);
    }

    /**
     * The same filter as {@link #accessorCandidates}, inverted on visibility: the methods that would
     * have been accessors if only they were public. Serves diagnostics rather than code generation —
     * a field nobody exposes and a field exposed only by a private method are different mistakes, and
     * "add an accessor" is the wrong advice for the second one.
     */
    public static List<Method> nonPublicAccessorCandidates(List<Method> methods) {
        return candidates(methods, false);
    }

    private static List<Method> candidates(List<Method> methods, boolean requirePublic) {
        return methods.stream()
                .filter(m -> m.isPublic() == requirePublic
                        && !m.isStatic()
                        && m.parameterCount() == 0
                        && !"void".equals(m.returnTypeFqn())
                        && !OBJECT_METHOD_NAMES.contains(m.name()))
                .toList();
    }

    /**
     * Returns the first candidate whose return type matches the property's type. Empty when no
     * candidate matches.
     */
    public static Optional<Method> findAccessor(Property property, List<Method> candidates) {
        return candidates.stream()
                .filter(m -> property.typeFqn().equals(m.returnTypeFqn()))
                .findFirst();
    }

    /**
     * High-level orchestrator: given a field and the full set of methods declared on its enclosing
     * type, returns the accessor method that exposes the field, or empty if none is found. Composes
     * {@link #toProperty}, {@link #toShape}, {@link #accessorCandidates}, the shape-based
     * {@link #findAccessor(Property, List)}, and {@link #matchesShape} into a single call so the
     * validator doesn't have to thread the intermediate {@code candidateShapes} list through.
     */
    public static Optional<ExecutableElement> findAccessor(
            VariableElement field, List<ExecutableElement> methods) {
        return findAccessor(field, methods, true);
    }

    /**
     * The non-public method that would have exposed {@code field}, or empty if there is none. Used to
     * tell the author to widen the accessor they already wrote instead of asking for another one; see
     * {@link #nonPublicAccessorCandidates}.
     */
    public static Optional<ExecutableElement> findNonPublicAccessor(
            VariableElement field, List<ExecutableElement> methods) {
        return findAccessor(field, methods, false);
    }

    private static Optional<ExecutableElement> findAccessor(
            VariableElement field, List<ExecutableElement> methods, boolean requirePublic) {
        var shapes = methods.stream().map(AccessorLookup::toShape).toList();
        var candidates = requirePublic ? accessorCandidates(shapes) : nonPublicAccessorCandidates(shapes);
        return findAccessor(toProperty(field), candidates)
                .flatMap(shape -> methods.stream()
                        .filter(m -> matchesShape(m, shape))
                        .findFirst());
    }
}
