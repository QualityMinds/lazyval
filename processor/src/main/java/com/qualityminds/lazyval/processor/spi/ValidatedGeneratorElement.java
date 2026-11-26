package com.qualityminds.lazyval.processor.spi;

import com.qualityminds.lazyval.naming.DotName;
import com.qualityminds.lazyval.naming.Payload;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;

/**
 * Information about a class that is annotated with @LazyVal suitable for generators to consume.
 * Low-Level access to the <code>java.lang.model.*</code> types is still possible in case generators need further
 * analysis.
 */
@ApiStatus.Experimental
public final class ValidatedGeneratorElement {

    private final TypeElement element;
    private final TypeMirror payloadType;
    private final @Nullable ExecutableElement factoryMethod;
    private final DotName name;
    private final JavaPayload java;

    private ValidatedGeneratorElement(TypeElement element, TypeMirror payloadType, @Nullable ExecutableElement factoryMethod,
                                      ExecutableElement accessor){
        this.element = element;
        this.payloadType = payloadType;
        this.factoryMethod = factoryMethod;
        name = DotNames.from(element);
        // A factory and a constructor are both matched on the payload type exactly, so whichever is called takes
        // a primitive precisely when the payload is one: that is what a boxed payload is unboxed to.
        java = new JavaPayload(new AccessPlan(name, accessor.getSimpleName() + "()",
                factoryMethod == null ? null : factoryMethod.getSimpleName().toString(),
                Payloads.primitiveKind(payloadType)));
    }

    /**
     * Describes a type every rule accepted, for the generators. Lazyval builds these; a generator receives them.
     *
     * @param element       the annotated element, or the one listed in {@code @LazyvalConfiguration#externalTypes()}
     * @param payloadType   the type of the payload
     * @param accessor      the public method reading the payload: a record component's accessor, or the method found
     *                      to expose a class's payload field
     * @param factoryMethod the factory method rebuilding an instance, or {@code null} to call the constructor
     * @return the element passed to each generator
     */
    @ApiStatus.Internal
    public static ValidatedGeneratorElement of(TypeElement element, TypeMirror payloadType, ExecutableElement accessor,
                                               @Nullable ExecutableElement factoryMethod){
        return new ValidatedGeneratorElement(element, payloadType, factoryMethod, accessor);
    }

    /**
     * This domain-primitive's own name, in the spellings generated code needs — most often
     * {@link DotName#flatName()}, to derive the name of a generated class from it:
     *
     * <pre>{@code
     * String codecName = element.name().flatName() + "Codec";   // "IdsProductIdCodec"
     * }</pre>
     *
     * <p>{@code flatName()} rather than the simple name, because a class or file name must not contain
     * a dot: two nested types called {@code ProductId} under different enclosing classes would
     * otherwise generate the same file, the second silently overwriting the first.
     *
     * @return name of the type, enclosing types included
     */
    public DotName name(){
        return name;
    }

    /**
     * Whether the annotated element is a record or not.
     * @return true if record, false otherwise
     */
    public boolean isRecord(){
        return element.getKind() == ElementKind.RECORD;
    }

    /**
     * Returns the low-level {@link TypeElement} annotated as LazyValue or listed in
     * {@code @LazyvalConfiguration#externalTypes()}.
     * Can be used to further analyze the code structure in case existing helper functions don't suffice.
     * @return the annotated/configured element.
     */
    public TypeElement element() {
        return element;
    }

    /**
     * Return the low-level {@link ExecutableElement} that holds the factory method.
     * Can be used to further analyze the code structure in case existing helper functions don't suffice.
     * @return factory method, if any.
     */
    public @Nullable ExecutableElement factoryMethod() {
        return factoryMethod;
    }

    /**
     * The type this domain-primitive carries — the low-level {@link TypeMirror}, to hand straight to
     * JavaPoet or to analyse further.
     *
     * <p>Note that {@code TypeName.get(payloadType()).box()} is safe to call unconditionally: JavaPoet
     * returns a reference type unchanged, so a generator writing a nullable slot does not need to
     * branch on {@link #isPayloadPrimitive()} first. A value held in such a slot goes back through
     * {@link JavaPayload#createFromBoxed(String)}, which needs no branch either.
     *
     * @return the payload type
     */
    public TypeMirror payloadType(){
        return payloadType;
    }

    /**
     * Whether the payload is a primitive, and so whether generated code can skip a null check.
     *
     * <p>Answered from the {@link TypeMirror} rather than from {@link #payload()}, which needs the
     * payload to have a name it can spell. The two cannot disagree — a {@link Payload.Primitive} is
     * produced exactly when the mirror reports a primitive kind — and asking the compiler keeps this
     * total. The Kotlin SPI has no such answer to ask for, since Kotlin has no primitives at source
     * level, so it derives the same boolean from its own {@code payload}.
     *
     * @return true when primitive, false otherwise
     */
    public boolean isPayloadPrimitive(){
        return payloadType.getKind().isPrimitive();
    }

    /**
     * The payload type's name, for the two things a code writer cannot give you: an identifier to build
     * a generated name from, and the reference name a primitive becomes where only an object will do.
     *
     * <pre>{@code
     * String method = "map" + element.payload().identifier() + "To" + element.name().flatName();
     * }</pre>
     *
     * <p>Computed on demand rather than stored, so a payload that has no spellable name — an array —
     * only fails for a generator that actually asks.
     *
     * @return the payload type's name
     * @throws IllegalStateException if the payload is neither a primitive nor a declared type
     */
    public Payload payload(){
        return Payloads.from(payloadType);
    }

    /**
     * Expressions for generated Java: reading the payload out of an instance, and rebuilding an
     * instance from a payload.
     *
     * <p>Everything that produces generator <em>output</em> lives behind this one member; everything
     * else on this element merely describes the domain-primitive. See {@link JavaPayload}.
     *
     * @return the expression facade
     */
    public JavaPayload java(){
        return java;
    }
}
