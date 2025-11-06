package de.qualityminds.lazyval.processor.spi;

import org.jetbrains.annotations.ApiStatus;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.Optional;

@ApiStatus.Experimental
public sealed interface ValidatedGeneratorElement permits RecordElement, ObjectElement {
    TypeElement element();

    Optional<ExecutableElement> factoryMethod();

    TypeMirror wrappedType();
    String wrappedTypeName();
}
