package com.qualityminds.lazyval.processor.spi;

import com.qualityminds.lazyval.naming.Payload;
import org.jspecify.annotations.NullUnmarked;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor14;

/**
 * Reads a payload type's name off what the annotation processing API reports.
 *
 * <p>The split between the two {@link Payload} cases is the compiler's own: {@code visitPrimitive}
 * against {@code visitDeclared}. Deciding it here, where the {@link TypeMirror} is authoritative, is
 * what keeps every generator downstream from re-deriving it from a string.
 */
final class Payloads {

    private Payloads() {
    }

    /**
     * @param type the payload type
     * @return its name
     * @throws IllegalStateException if the payload is neither a primitive nor a declared type, which
     *         has no name a generator could spell — an array payload, say
     */
    static Payload from(TypeMirror type) {
        @SuppressWarnings("RedundantCast") // needed due to NullMarked
        Payload name = type.accept(new PayloadVisitor(), (Void) null);
        return name;
    }
}

@NullUnmarked
class PayloadVisitor extends SimpleTypeVisitor14<Payload, Void> {

    @Override
    public Payload visitPrimitive(PrimitiveType t, Void unused) {
        return new Payload.Primitive(switch (t.getKind()) {
            case BOOLEAN -> Payload.Kind.BOOLEAN;
            case BYTE -> Payload.Kind.BYTE;
            case SHORT -> Payload.Kind.SHORT;
            case INT -> Payload.Kind.INT;
            case LONG -> Payload.Kind.LONG;
            case CHAR -> Payload.Kind.CHAR;
            case FLOAT -> Payload.Kind.FLOAT;
            case DOUBLE -> Payload.Kind.DOUBLE;
            default -> throw new IllegalStateException("Not a primitive kind: " + t.getKind());
        });
    }

    @Override
    public Payload visitDeclared(DeclaredType t, Void unused) {
        return new Payload.Declared(DotNames.from((TypeElement) t.asElement()));
    }

    @Override
    protected Payload defaultAction(TypeMirror t, Void unused) {
        throw new IllegalStateException(
                "A payload of type '" + t + "' (" + t.getKind() + ") has no name generated code could "
                        + "spell. Lazyval names a payload that is either a primitive or a declared type.");
    }
}
