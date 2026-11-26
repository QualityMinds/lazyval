package com.qualityminds.lazyval.processor.internal;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.List;

/**
 * How a type is rebuilt from its payload: the factory method or constructor generated code calls, or the reason
 * there is none to call. Found by Lazyval's conventions and judged by {@link LazyvalElementValidator}.
 *
 * <p>The conventions: a factory method is a static method returning the type and taking a single parameter of the
 * payload type, whatever its name, and the constructor is the one taking the payload type and nothing else. A
 * factory is what generated code calls wherever there is one, even with the constructor in reach, because a factory
 * is where a type validates what it is built from.
 */
sealed interface CreatorLookup {

    /** What generated code calls to rebuild the value, once the rules agree it may. */
    sealed interface Found extends CreatorLookup {

        /**
         * The factory method or constructor itself.
         *
         * @return its declaration
         */
        ExecutableElement declaration();
    }

    /**
     * The one factory method.
     *
     * @param declaration the factory method
     */
    record Factory(ExecutableElement declaration) implements Found {}

    /**
     * No factory method, but a constructor taking exactly the payload.
     *
     * @param declaration the constructor
     */
    record Constructor(ExecutableElement declaration) implements Found {}

    /**
     * More than one factory method, and no telling which one is meant to rebuild the value.
     *
     * @param factories each of them, in declaration order
     */
    record SeveralFactories(List<ExecutableElement> factories) implements CreatorLookup {}

    /** Neither a factory method nor a constructor taking the payload. */
    record Missing() implements CreatorLookup {}

    /**
     * Looks up how {@code type} is rebuilt from a {@code payloadType}.
     *
     * @param type        the type
     * @param payloadType the type of its payload
     * @param types       the compiler's type utilities, to compare a parameter with the payload type
     * @return the way back in, or why there is none
     */
    static CreatorLookup of(TypeElement type, TypeMirror payloadType, Types types) {
        var takingPayload = type.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD || element.getKind() == ElementKind.CONSTRUCTOR)
                .map(element -> (ExecutableElement) element)
                .filter(executable -> executable.getParameters().size() == 1
                        && types.isSameType(executable.getParameters().get(0).asType(), payloadType))
                .toList();
        var factories = takingPayload.stream()
                .filter(method -> method.getKind() == ElementKind.METHOD
                        && method.getModifiers().contains(Modifier.STATIC)
                        && types.isSameType(method.getReturnType(), type.asType()))
                .toList();
        if (factories.size() > 1) {
            return new SeveralFactories(factories);
        }
        if (factories.size() == 1) {
            return new Factory(factories.get(0));
        }
        return takingPayload.stream()
                .filter(executable -> executable.getKind() == ElementKind.CONSTRUCTOR)
                .findFirst()
                .<CreatorLookup>map(Constructor::new)
                .orElseGet(Missing::new);
    }
}
