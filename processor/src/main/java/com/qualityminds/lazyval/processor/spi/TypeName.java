package com.qualityminds.lazyval.processor.spi;

import org.jspecify.annotations.NullUnmarked;

import javax.lang.model.element.Name;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.util.SimpleTypeVisitor14;

/**
 * Inspired by Javapoet's 'TypeName', provides a simple way to represent a type name with the
 * ability for boxing and unboxing.
 * Is usually constructed by Lazyval via a {@link SimpleTypeVisitor14}.
 * @param simpleName unqualified name of the type
 */
public record TypeName(String simpleName){
    public static final TypeName BOOLEAN = new TypeName("boolean");
    public static final TypeName BOOLEAN_BOXED = new TypeName("Boolean");
    public static final TypeName BYTE = new TypeName("byte");
    public static final TypeName BYTE_BOXED = new TypeName("Byte");
    public static final TypeName SHORT = new TypeName("short");
    public static final TypeName SHORT_BOXED = new TypeName("Short");
    public static final TypeName INT = new TypeName("int");
    public static final TypeName INT_BOXED = new TypeName("Integer");
    public static final TypeName LONG = new TypeName("long");
    public static final TypeName LONG_BOXED = new TypeName("Long");
    public static final TypeName CHAR = new TypeName("char");
    public static final TypeName CHAR_BOXED = new TypeName("Character");
    public static final TypeName FLOAT = new TypeName("float");
    public static final TypeName FLOAT_BOXED = new TypeName("Float");
    public static final TypeName DOUBLE = new TypeName("double");
    public static final TypeName DOUBLE_BOXED = new TypeName("Double");

    TypeName(Name name) {
        this(name.toString());
    }


    @Override
    public String toString() {
        return simpleName;
    }

    public boolean isBoxedPrimitive() {
        return this.equals(TypeName.BOOLEAN_BOXED)
                || this.equals(TypeName.BYTE_BOXED)
                || this.equals(TypeName.SHORT_BOXED)
                || this.equals(TypeName.INT_BOXED)
                || this.equals(TypeName.LONG_BOXED)
                || this.equals(TypeName.CHAR_BOXED)
                || this.equals(TypeName.FLOAT_BOXED)
                || this.equals(TypeName.DOUBLE_BOXED);
    }

    /**
     * Returns a boxed type if this is a primitive type (like {@code Integer} for {@code int}) or
     * {@code void}. Returns this type if boxing doesn't apply.
     *
     * @throws UnsupportedOperationException if this type isn't eligible for boxing.
     */
    public TypeName box() {
        TypeName boxed;
        if (simpleName.equals(BOOLEAN.simpleName)) {
            boxed = TypeName.BOOLEAN_BOXED;
        } else if (simpleName.equals(BYTE.simpleName)) {
            boxed = TypeName.BYTE_BOXED;
        } else if (simpleName.equals(SHORT.simpleName)) {
            boxed = TypeName.SHORT_BOXED;
        } else if (simpleName.equals(INT.simpleName)) {
            boxed = TypeName.INT_BOXED;
        } else if (simpleName.equals(LONG.simpleName)) {
            boxed = TypeName.LONG_BOXED;
        } else if (simpleName.equals(CHAR.simpleName)) {
            boxed = TypeName.CHAR_BOXED;
        } else if (simpleName.equals(FLOAT.simpleName)) {
            boxed = TypeName.FLOAT_BOXED;
        } else if (simpleName.equals(DOUBLE.simpleName)) {
            boxed = TypeName.DOUBLE_BOXED;
        } else {
            throw new UnsupportedOperationException("Cannot box " + simpleName);
        }
        return boxed;
    }

    /**
     * Returns an unboxed type if this is a boxed primitive type (like {@code int} for {@code
     * Integer}) or {@code Void}. Returns this type if it is already unboxed.
     *
     * @throws UnsupportedOperationException if this type isn't eligible for unboxing.
     */
    public TypeName unbox() {
        TypeName unboxed;
        if (this.equals(TypeName.BOOLEAN_BOXED)) {
            unboxed = BOOLEAN;
        } else if (this.equals(TypeName.BYTE_BOXED)) {
            unboxed = BYTE;
        } else if (this.equals(TypeName.SHORT_BOXED)) {
            unboxed = SHORT;
        } else if (this.equals(TypeName.INT_BOXED)) {
            unboxed = INT;
        } else if (this.equals(TypeName.LONG_BOXED)) {
            unboxed = LONG;
        } else if (this.equals(TypeName.CHAR_BOXED)) {
            unboxed = CHAR;
        } else if (this.equals(TypeName.FLOAT_BOXED)) {
            unboxed = FLOAT;
        } else if (this.equals(TypeName.DOUBLE_BOXED)) {
            unboxed = DOUBLE;
        } else {
            throw new UnsupportedOperationException("Cannot unbox " + this.simpleName);
        }
        return unboxed;
    }
}

class TypeNameVisitor extends SimpleTypeVisitor14<TypeName, Void> {
    @Override
    public TypeName visitPrimitive(PrimitiveType t, Void unused) {
        return switch (t.getKind()) {
            case BOOLEAN -> TypeName.BOOLEAN;
            case BYTE -> TypeName.BYTE;
            case SHORT -> TypeName.SHORT;
            case INT -> TypeName.INT;
            case LONG -> TypeName.LONG;
            case CHAR -> TypeName.CHAR;
            case FLOAT -> TypeName.FLOAT;
            case DOUBLE -> TypeName.DOUBLE;
            default -> throw new IllegalStateException();
        };
    }

    @NullUnmarked
    @Override
    public TypeName visitDeclared(DeclaredType t, Void unused) {
        return new TypeName(t.asElement().getSimpleName());
    }
}