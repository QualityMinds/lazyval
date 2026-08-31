package scenarios.jpa;

import com.qualityminds.lazyval.LazyValue;

import jakarta.persistence.Transient;

/**
 * Two values, the derived one carrying JPA's {@code @Transient} on the field — the field-access
 * placement. Without transient handling this is the "one non-transient value" error.
 * <p>
 * The derived value is deliberately typed differently from the wrapped one: {@code AccessorLookup}
 * pairs by return type, so two same-typed values would both resolve to the first matching getter.
 */
@LazyValue
public final class JpaTransientField {

    private final String value;

    @Transient
    private final int derivedLength;

    private JpaTransientField(String value) {
        this.value = value;
        this.derivedLength = value.length();
    }

    public String getValue() {
        return value;
    }

    public int getDerivedLength() {
        return derivedLength;
    }

    public static JpaTransientField of(String value) {
        if (value == null) {
            return null;
        }
        return new JpaTransientField(value);
    }
}
