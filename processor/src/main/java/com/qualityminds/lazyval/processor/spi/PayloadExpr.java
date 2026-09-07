package com.qualityminds.lazyval.processor.spi;

import com.qualityminds.lazyval.naming.DotName;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A generated expression, with the type names it mentions kept apart from its text.
 *
 * <p>Keeping them apart is the whole point: a generator that manages its own imports needs to know
 * which types an expression names and where, and passing that back as text would leave it to guess.
 * Both halves are available without this SPI having to know anything about JavaPoet — it describes the
 * expression, and the generator renders it.
 *
 * <p>Use {@link #asSource()} to write the expression as-is, or {@link #asFormat(String)} to hand the
 * type names to a code writer. The Kotlin SPI's {@code PayloadExpr} is the same type for the same
 * reason, so a generator that ships for both processors reads the same way on each.
 */
@ApiStatus.Experimental
public final class PayloadExpr {

    private final List<Part> parts;

    PayloadExpr(List<Part> parts) {
        this.parts = List.copyOf(parts);
    }

    /** Package-private: the split between text and type is Lazyval's to decide, not a generator's. */
    sealed interface Part {
        record Text(String text) implements Part {}

        record Type(DotName name, String source) implements Part {}
    }

    /**
     * The expression as source text, every type it names spelled out canonically, so it compiles with
     * no import at all.
     *
     * <p>Also what {@link #toString()} returns, so an expression can go straight into a template.
     *
     * @return the expression
     */
    public String asSource() {
        return parts.stream()
                .map(part -> part instanceof Part.Type type ? type.source() : ((Part.Text) part).text())
                .collect(Collectors.joining());
    }

    /**
     * The expression with {@code typeSlot} in place of every type it names, and those types in the
     * order they appear — the two things JavaPoet's {@code $T} needs in order to add the imports itself.
     *
     * <pre>{@code
     * var formatted = element.java().create("value").asFormat("$T");
     * Object[] args = formatted.types().stream().map(JavaPoetExprs::className).toArray();
     * method.addStatement("return " + formatted.format(), args);
     * }</pre>
     *
     * @param typeSlot what to write where a type belongs
     * @return the format and the types its slots stand for
     */
    public Formatted asFormat(String typeSlot) {
        String format = parts.stream()
                .map(part -> part instanceof Part.Type ? typeSlot : ((Part.Text) part).text())
                .collect(Collectors.joining());
        List<DotName> types = parts.stream()
                .filter(Part.Type.class::isInstance)
                .map(part -> ((Part.Type) part).name())
                .toList();
        return new Formatted(format, types);
    }

    /** @see #asSource() */
    @Override
    public String toString() {
        return asSource();
    }

    /**
     * {@link #asFormat(String)}'s two halves.
     *
     * @param format the expression, with a slot wherever a type belongs
     * @param types the types those slots stand for, in the order the slots appear
     */
    public record Formatted(String format, List<DotName> types) {
        public Formatted {
            types = List.copyOf(types);
        }
    }
}
