package de.qualityminds.lazyval.processor.spi;

import org.jetbrains.annotations.ApiStatus;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.Optional;

@ApiStatus.Experimental
public record RecordElement(TypeElement element, RecordComponentElement field, Optional<ExecutableElement> factoryMethod)
        implements ValidatedGeneratorElement {
    @Override
    public TypeMirror wrappedType() {
        return field.asType();
    }

    @Override
    public String wrappedTypeName() {
        return field.getSimpleName().toString();
    }
}
