package de.qualityminds.lazyval.processor;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.Optional;

sealed interface ValidatedGeneratorElement permits RecordElement, ObjectElement {
    TypeElement element();

    Optional<ExecutableElement> factoryMethod();

    TypeMirror wrappedType();
    String wrappedTypeName();
}
