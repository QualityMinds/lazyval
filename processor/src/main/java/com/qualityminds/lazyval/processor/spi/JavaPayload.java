package com.qualityminds.lazyval.processor.spi;

import org.jetbrains.annotations.ApiStatus;

/**
 * Java expressions that read a domain-primitive's payload and rebuild it.
 *
 * <p>Reached through {@link ValidatedGeneratorElement#java()}. A generator asks for a whole expression
 * and writes it out; it never assembles one from an accessor name and a type name of its own. Each
 * call takes the name of a variable in the code being generated, and returns the expression to put
 * there.
 *
 * <h2>What comes back</h2>
 *
 * <pre>{@code
 * package com.acme;
 *
 * public record ProductId(String value) { }
 *
 * public final class Isbn {                        // private constructor
 *     public String getValue() { ... }
 *     public static Isbn parse(String value) { ... }
 * }
 *
 * public record Quantity(int value) { }
 * }</pre>
 *
 * <pre>{@code
 * // given ProductId id, String raw
 * element.java().read("id")        // id.value()
 * element.java().create("raw")     // new com.acme.ProductId(raw)
 *
 * // given Isbn isbn, String raw
 * element.java().read("isbn")      // isbn.getValue()
 * element.java().create("raw")     // com.acme.Isbn.parse(raw)
 *
 * // given Integer boxed, e.g. in an AttributeConverter<Quantity, Integer>
 * element.java().createFromBoxed("boxed")    // new com.acme.Quantity((int) boxed)
 * }</pre>
 *
 * <p>A factory is called wherever one exists, so generated code never constructs around the checks it
 * makes. The accessor is resolved by return type rather than by name, so a getter not named after the
 * field — including one on an external type such as {@code java.time.Year.getValue()} — is found the
 * same way. Types arrive spelled canonically, which is what makes a nested {@code Ids.ProductId}
 * resolve with no import; {@link PayloadExpr#asFormat(String)} hands them to JavaPoet's {@code $T}
 * instead.
 *
 * <p>There is no {@code readOrNull} or {@code createOrNull} to match the four methods on the Kotlin
 * facade. Java has neither a safe call nor {@code let}, so a null-tolerant conversion is an
 * {@code if} the generator writes around the expression, guarding on
 * {@link ValidatedGeneratorElement#isPayloadPrimitive()}.
 *
 * <p>The indirection buys less here than on the Kotlin side, where {@code @JvmName},
 * {@code internal} and {@code value class} all move the spelling out from under a generator. What it
 * buys is that the two SPIs read the same, and that everything which produces <em>generator
 * output</em> sits behind one member rather than mixed in among the ones that merely describe the
 * element.
 */
@ApiStatus.Experimental
public final class JavaPayload {

    private final AccessPlan plan;

    JavaPayload(AccessPlan plan) {
        this.plan = plan;
    }

    /**
     * Reads the payload out of {@code instance}.
     *
     * <p>For a {@code ProductId id}, {@code read("id")} is {@code id.value()}; for an
     * {@code Isbn isbn}, {@code isbn.getValue()}.
     *
     * @param instance name of the variable holding the domain-primitive
     * @return the expression
     */
    public PayloadExpr read(String instance) {
        return plan.read(instance);
    }

    /**
     * Rebuilds the domain-primitive from {@code payload}.
     *
     * <p>For a {@code String raw}, {@code create("raw")} is {@code new com.acme.ProductId(raw)}, or
     * {@code com.acme.Isbn.parse(raw)} where the type has a factory.
     *
     * <p>{@code payload} has to be of the payload type itself. A primitive payload held boxed goes
     * through {@link #createFromBoxed(String)} instead.
     *
     * @param payload name of the variable holding a payload value
     * @return the expression
     */
    public PayloadExpr create(String payload) {
        return plan.create(payload);
    }

    /**
     * Rebuilds the domain-primitive from {@code boxedPayload}, a payload held as a reference type — as
     * by a generator whose framework interface is typed on one, e.g.
     * {@code AttributeConverter<Quantity, Integer>}.
     *
     * <p>For an {@code int} payload and an {@code Integer dbValue}, {@code createFromBoxed("dbValue")}
     * is {@code com.acme.Quantity.of((int) dbValue)}. Unboxed first, the value binds to the factory or
     * constructor Lazyval matched. Passed to {@link #create(String)} as it is, it would bind to an
     * overload for the boxed type, such as {@code of(Integer)}, wherever the type declares one: javac
     * prefers an overload that applies without unboxing. For any other payload this is
     * {@link #create(String)}, so a generator does not need to branch on
     * {@link ValidatedGeneratorElement#isPayloadPrimitive()}.
     *
     * <p>A payload held as its own type stays with {@link #create(String)}: casting an {@code int}
     * that already is one is a redundant cast, which {@code -Xlint:cast} reports.
     *
     * @param boxedPayload name of the variable holding the boxed payload value. A method call works as
     *                     well; an expression with operators does not, as the cast would bind to its
     *                     first operand only
     * @return the expression
     */
    public PayloadExpr createFromBoxed(String boxedPayload) {
        return plan.createFromBoxed(boxedPayload);
    }
}
