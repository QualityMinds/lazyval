package com.qualityminds.lazyval.processor.spi;

import com.qualityminds.lazyval.naming.DotName;
import com.qualityminds.lazyval.naming.Payload;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Everything needed to spell the expressions that read a domain-primitive's payload and rebuild it —
 * and nothing that needs a compiler to look at.
 *
 * <p>Deliberately made of plain strings and a {@link DotName}. The decisions that need the annotation
 * processing API — which member is the accessor, whether there is a factory — are taken once during
 * validation; what is left is string assembly, which belongs somewhere it can be asserted directly
 * rather than by compiling a scenario and reading the generated file. The Kotlin SPI splits the same
 * way, for the same reason.
 *
 * @param name the domain-primitive's own name
 * @param accessorFragment how the payload is read off an instance, {@code "value()"}
 * @param factoryName simple name of the factory, or {@code null} to call the constructor
 * @param primitive the payload's kind if it is a primitive, or {@code null}; what a boxed payload is
 *                  unboxed to
 */
record AccessPlan(DotName name, String accessorFragment, @Nullable String factoryName,
                  Payload.@Nullable Kind primitive) {

    /** {@code instance.value()} — no type is named, so this needs no import either way. */
    PayloadExpr read(String instance) {
        return new PayloadExpr(List.of(new PayloadExpr.Part.Text(instance + "." + accessorFragment)));
    }

    /** {@code Ids.ProductId.of(v)} or {@code new Ids.ProductId(v)}, whichever the type offers. */
    PayloadExpr create(String payload) {
        var type = new PayloadExpr.Part.Type(name, name.canonicalName());
        if (factoryName != null) {
            return new PayloadExpr(List.of(
                    type, new PayloadExpr.Part.Text("." + factoryName + "(" + payload + ")")));
        }
        return new PayloadExpr(List.of(
                new PayloadExpr.Part.Text("new "), type,
                new PayloadExpr.Part.Text("(" + payload + ")")));
    }

    /**
     * {@code Quantity.of((int) v)} for a primitive payload, {@link #create(String)} for any other. Casts
     * only here, because a cast of a value that already is an {@code int} is redundant.
     */
    PayloadExpr createFromBoxed(String boxedPayload) {
        return create(primitive == null ? boxedPayload : "(" + primitive.keyword() + ") " + boxedPayload);
    }
}
