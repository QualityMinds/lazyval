package scenarios.jpa;

import com.qualityminds.lazyval.LazyValue;

import jakarta.persistence.Transient;

/**
 * Counterpart to {@code JpaTransientField}: the same shape with JPA's {@code @Transient} on the
 * accessor instead of the field — the property-access placement, which is what an entity using
 * getter access declares.
 * <p>
 * The derived value is deliberately typed differently from the wrapped one: {@code AccessorLookup}
 * pairs by return type, so two same-typed values would both resolve to the first matching getter
 * and the annotated one would never be consulted.
 */
@LazyValue
public final class JpaTransientGetter {

    private final String value;

    private final int derivedLength;

    private JpaTransientGetter(String value) {
        this.value = value;
        this.derivedLength = value.length();
    }

    public String getValue() {
        return value;
    }

    @Transient
    public int getDerivedLength() {
        return derivedLength;
    }

    public static JpaTransientGetter of(String value) {
        if (value == null) {
            return null;
        }
        return new JpaTransientGetter(value);
    }
}
