package com.qualityminds.lazyval.processor.spi;

import com.qualityminds.lazyval.naming.DotName;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Builds a {@link DotName} out of what the annotation processing API reports.
 *
 * <p>{@link TypeElement} does not offer a package and a list of simple names; it offers a simple name
 * and an enclosing element, which is either another type or the package. Assembling the split here —
 * once, at the boundary, where the compiler is the authority — is what lets everything downstream read
 * a spelling off a {@code DotName} instead of guessing the split back out of a qualified string.
 */
final class DotNames {

    private DotNames() {
    }

    /**
     * Splits an element's name into a package and simple names, walking outwards to whatever encloses it.
     *
     * @param element the annotated type
     * @return its name, enclosing types included
     */
    static DotName from(TypeElement element) {
        Deque<String> simpleNames = new ArrayDeque<>();
        Element current = element;
        // Only a type encloses a type name. The walk ends at the package, or at anything else that
        // could not be written as a qualifier either.
        while (current instanceof TypeElement type) {
            simpleNames.addFirst(type.getSimpleName().toString());
            current = type.getEnclosingElement();
        }
        String packageName = current instanceof PackageElement pkg
                ? pkg.getQualifiedName().toString()
                : "";
        return new DotName(packageName, List.copyOf(simpleNames));
    }
}
