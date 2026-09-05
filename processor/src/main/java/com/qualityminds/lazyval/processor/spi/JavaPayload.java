package com.qualityminds.lazyval.processor.spi;

import org.jetbrains.annotations.ApiStatus;

/**
 * Java expressions that read a domain-primitive's payload and rebuild it.
 *
 * <p>Reached through {@link ValidatedGeneratorElement#java()}. A generator asks for a whole expression
 * and writes it out; it never assembles one from an accessor name and a type name of its own.
 *
 * <p>That indirection buys less here than on the Kotlin side, where {@code @JvmName}, {@code internal}
 * and {@code value class} all move the spelling out from under a generator. What it buys is that the
 * two SPIs read the same, and that everything which produces <em>generator output</em> sits behind one
 * member rather than mixed in among the ones that merely describe the element:
 *
 * <pre>{@code
 * element.java().read("source")     // source.value()
 * element.java().create("value")    // new Ids.ProductId(value), or Ids.ProductId.of(value)
 * }</pre>
 *
 * <p>Which of constructor or factory to call, and whether the accessor is a record component or a
 * getter, are resolved during validation — so a generator never has to know which case it is in.
 */
@ApiStatus.Experimental
public final class JavaPayload {

    private final AccessPlan plan;

    JavaPayload(AccessPlan plan) {
        this.plan = plan;
    }

    /**
     * Reads the payload out of {@code instance}.
     * @param instance name of the variable holding the domain-primitive
     * @return the expression
     */
    public PayloadExpr read(String instance) {
        return plan.read(instance);
    }

    /**
     * Rebuilds the domain-primitive from {@code payload}.
     * @param payload name of the variable holding a payload value
     * @return the expression
     */
    public PayloadExpr create(String payload) {
        return plan.create(payload);
    }
}
