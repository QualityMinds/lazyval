package com.qualityminds.lazyval.naming;

import java.util.Objects;

/**
 * What a domain-primitive carries, described the way the JVM sees it — the one thing generated code has
 * to spell, whichever language it is written in.
 *
 * <p>Two cases, because the JVM really has two. A reference payload is a declaration, so it carries a
 * {@link DotName} and everything {@code DotName} offers applies to it. A primitive payload is a
 * <em>keyword</em>: {@code int} has no package, no enclosing type and no canonical name, so treating it
 * as a name at all is a category error — which is why this is a sealed pair rather than one type with a
 * flag to check.
 *
 * <pre>{@code
 * String method = "map" + element.payload().identifier() + "To" + element.name().flatName();
 *
 * if (element.payload() instanceof Payload.Primitive primitive) {
 *     writeNumberOrBoolean(primitive.kind());       // int, long, boolean, ...
 * }
 * }</pre>
 *
 * <h2>What this is not</h2>
 *
 * <p>Not the payload <em>value</em>, nor the property holding it, nor how to read it. Reading and
 * rebuilding are whole expressions the SPI hands out separately, so that no generator assembles one
 * from parts.
 *
 * <p>Not a <em>type</em> either: no type arguments, no nullability, no arrays. Those differ between the
 * two languages Lazyval generates, and JavaPoet and KotlinPoet already model them well — so a generator
 * that needs a type asks the element for the payload type itself and hands that to its own code writer.
 * Which is why this lives beside {@link DotName} rather than in a package of its own: what remains once
 * the compiler's type object is set aside is a naming question, and exactly the two answers a code
 * writer cannot give you — an identifier safe to build a generated name from, and the reference name a
 * primitive has to become where only an object will do.
 *
 * <p>There is deliberately no {@code isPrimitive()}. On a sealed pair that question is what the type
 * system already answers — {@code instanceof Payload.Primitive}, or a {@code when} the compiler checks
 * for exhaustiveness — and a boolean beside it would be a second way to ask one thing. A generator that
 * only wants to know whether to emit a null check asks the element instead, which answers it without
 * needing the payload to have a spellable name at all.
 *
 * <p>Not the payload as <em>declared</em>, finally. A Kotlin {@code value class} is compiled away, so
 * what is described here is the type at the end of the wrapping chain: what survives to runtime, and so
 * what a framework can persist, serialize or validate.
 */
public sealed interface Payload {

    /**
     * A fragment safe to build a generated identifier from — {@code "Int"}, {@code "String"},
     * {@code "IdsProductId"}.
     *
     * <p>Never contains a dot, which is the trap this exists to close: a nested payload type spelled
     * {@code Ids.ProductId} turns {@code map<Payload>To<Domain>} into a method name that does not
     * compile, and a flattened one keeps two same-named nested types apart.
     *
     * @return the fragment
     */
    String identifier();

    /**
     * The payload's name where only a reference type will do — a generic argument, a
     * {@code Converter<A, B>}, a nullable field. {@code java.lang.Integer} for {@code int}; unchanged
     * for a reference payload.
     *
     * <p>Boxing runs one way only. Unboxing {@code Integer} to {@code int} narrows a signature to one
     * that can no longer carry {@code null}, which is the opposite of what a generator wants from a
     * reference payload.
     *
     * @return the reference-typed name
     */
    DotName boxed();

    /**
     * A primitive payload, held as its {@link Kind} rather than as a name, because a keyword is not one.
     * Match on this to emit a per-primitive statement; {@link #identifier()} and {@link #boxed()} cover
     * the cases where the kind itself does not matter.
     *
     * @param kind which primitive
     */
    record Primitive(Kind kind) implements Payload {

        public Primitive {
            Objects.requireNonNull(kind, "kind");
        }

        /** @return {@code "Int"} for {@code int}, {@code "Char"} for {@code char} */
        @Override
        public String identifier() {
            String keyword = kind.keyword();
            return Character.toUpperCase(keyword.charAt(0)) + keyword.substring(1);
        }

        /** @return {@code java.lang.Integer} for {@code int} */
        @Override
        public DotName boxed() {
            return kind.boxed();
        }

        /** @return the keyword, {@code "int"} */
        @Override
        public String toString() {
            return kind.keyword();
        }
    }

    /**
     * A reference payload, which is a declaration and so has a real name.
     *
     * @param name the payload type's own name, enclosing types and package included
     */
    record Declared(DotName name) implements Payload {

        public Declared {
            Objects.requireNonNull(name, "name");
        }

        /** @return {@link DotName#flatName()}, so a nested payload yields a usable identifier */
        @Override
        public String identifier() {
            return name.flatName();
        }

        /** @return the name unchanged; a reference type is already one */
        @Override
        public DotName boxed() {
            return name;
        }

        /** @return {@link DotName#canonicalName()} */
        @Override
        public String toString() {
            return name.canonicalName();
        }
    }

    /** The eight JVM primitives, each with the keyword it is written as and the class it boxes to. */
    enum Kind {
        BOOLEAN("boolean", "Boolean"),
        BYTE("byte", "Byte"),
        SHORT("short", "Short"),
        INT("int", "Integer"),
        LONG("long", "Long"),
        CHAR("char", "Character"),
        FLOAT("float", "Float"),
        DOUBLE("double", "Double");

        private final String keyword;
        private final DotName boxed;

        Kind(String keyword, String boxedSimpleName) {
            this.keyword = keyword;
            this.boxed = DotName.of("java.lang", boxedSimpleName);
        }

        /**
         * How the primitive is written in source.
         *
         * @return the keyword, {@code "int"} for {@link #INT}
         */
        public String keyword() {
            return keyword;
        }

        /**
         * The class the primitive boxes to, for a generator that has to name a reference type.
         *
         * @return the wrapper class, {@code java.lang.Integer} for {@link #INT}
         */
        public DotName boxed() {
            return boxed;
        }
    }
}
