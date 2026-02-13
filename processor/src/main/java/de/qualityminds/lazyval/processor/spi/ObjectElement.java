package de.qualityminds.lazyval.processor.spi;

import org.jetbrains.annotations.ApiStatus;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.Optional;

@ApiStatus.Experimental
@SuppressWarnings("doclint:accessibility,missing") // remove once api stable
public record ObjectElement(TypeElement element, VariableElement field, Optional<ExecutableElement> factoryMethod)
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
