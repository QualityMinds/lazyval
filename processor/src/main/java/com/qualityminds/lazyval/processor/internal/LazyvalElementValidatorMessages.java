package com.qualityminds.lazyval.processor.internal;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Every diagnostic {@link LazyvalElementValidator} reports about an unreachable declaration.
 * <p>
 * They live together because they are held to two consistency requirements that are easy to break
 * one message at a time: {@code ApIT} asserts each of them verbatim.
 */
final class LazyvalElementValidatorMessages {

    private LazyvalElementValidatorMessages() {}

    /**
     * The type itself rather than a member of it, and the first thing generated code needs: it has to
     * be able to name the domain-primitive before any accessor or constructor matters. Java has no
     * equivalent of Kotlin's {@code internal}, so {@code public} is the only way out.
     */
    static String nonPublicTypeMessage(TypeElement lazyvalElement) {
        return "Type '" + lazyvalElement.getSimpleName() + "' is " + visibilityOf(lazyvalElement)
                + " and cannot be referenced from generated code, which is emitted into another package."
                + " Make the type public.";
    }

    /**
     * Why generated code cannot read a field, phrased as the change the author has to make. Naming
     * the accessor is the point: the author can see it in their editor, so a message claiming
     * nothing was found reads as a bug in Lazyval rather than as a rule of Lazyval.
     */
    static String nonPublicAccessorMessage(VariableElement field, ExecutableElement accessor) {
        return "Accessor '" + accessor.getSimpleName() + "()' for field '" + field.getSimpleName()
                + "' is " + visibilityOf(accessor) + " and cannot be called from generated code, which is"
                + " emitted into another package. Make the accessor public.";
    }

    /**
     * The case with no KSP counterpart: a Kotlin property always has a getter, so only Java can offer
     * a field that nothing exposes. Advice therefore differs — add an accessor rather than widen one.
     */
    static String missingAccessorMessage(VariableElement field) {
        return "Field '" + field.getSimpleName() + "' has no public accessor. Lazyval reads the payload"
                + " through its accessor, which has to be public because generated code is emitted into"
                + " another package. Add a public accessor returning " + field.asType() + ".";
    }

    /**
     * The advice is the mirror image of {@link #nonPublicConstructorMessage}: an author who wrote a
     * factory meant that to be the way in, so they are pointed at widening it rather than at the
     * constructor they deliberately hid behind it.
     */
    static String nonPublicFactoryMessage(ExecutableElement factory) {
        return "Factory method '" + factory.getSimpleName() + "("
                + factory.getParameters().get(0).asType() + ")' is " + visibilityOf(factory)
                + " and cannot be called from generated code, which is emitted into another package."
                + " Make the factory method public, or add a public constructor.";
    }

    /**
     * Mirrors {@link #nonPublicAccessorMessage}, down to the naming of the visibility: the same
     * package boundary is at fault, so the same sentence should explain it.
     */
    static String nonPublicConstructorMessage(TypeElement lazyvalElement, ExecutableElement constructor) {
        return "Constructor '" + lazyvalElement.getSimpleName() + "("
                + constructor.getParameters().get(0).asType() + ")' is " + visibilityOf(constructor)
                + " and cannot be called from generated code, which is emitted into another package."
                + " Make the constructor public, or add a public static factory method.";
    }

    /**
     * Nothing to point at, so this one names the type and the payload it cannot be rebuilt from.
     * <p>
     * A record with derived state is the case worth spelling out: the transient components are the
     * reason the canonical constructor no longer matches, and naming them says why a record that
     * looks like it wraps one value cannot be rebuilt from that value.
     */
    static String missingReconstructionMessage(TypeElement lazyvalElement,
                                               TypeMirror payloadType,
                                               List<? extends Element> transientComponents) {
        if (lazyvalElement.getKind() == ElementKind.RECORD && !transientComponents.isEmpty()) {
            var names = transientComponents.stream()
                    .map(component -> "'" + component.getSimpleName() + "'")
                    .collect(Collectors.joining(", "));
            return "Record '" + lazyvalElement.getSimpleName() + "' cannot be reconstructed from its payload"
                    + " alone: the canonical constructor also takes the transient "
                    + (transientComponents.size() == 1 ? "component " : "components ") + names
                    + ". Add a constructor taking only " + payloadType + ", or a public static factory method.";
        }
        return (lazyvalElement.getKind() == ElementKind.RECORD ? "Record '" : "Class '")
                + lazyvalElement.getSimpleName() + "' cannot be reconstructed from its payload: no constructor"
                + " takes a single " + payloadType + ". Add one, or a public static factory method.";
    }

    /**
     * How every diagnostic here names a visibility. Java has no keyword for the default, so it is
     * spelled out the way the language specification's readers name it.
     */
    private static String visibilityOf(Element element) {
        var modifiers = element.getModifiers();
        if (modifiers.contains(Modifier.PRIVATE)) {
            return "private";
        }
        if (modifiers.contains(Modifier.PROTECTED)) {
            return "protected";
        }
        return "package-private";
    }
}
