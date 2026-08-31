package scenarios.springdata;

import com.qualityminds.lazyval.LazyValue;

import org.springframework.data.annotation.Transient;

/**
 * Counterpart to {@code SpringDataTransientField}: the same shape with Spring Data's
 * {@code @Transient} on the accessor instead of the field.
 * <p>
 * The derived value is deliberately typed differently from the wrapped one: {@code AccessorLookup}
 * pairs by return type, so two same-typed values would both resolve to the first matching getter
 * and the annotated one would never be consulted.
 */
@LazyValue
public final class SpringDataTransientGetter {

    private final String value;

    private final int derivedLength;

    private SpringDataTransientGetter(String value) {
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

    public static SpringDataTransientGetter of(String value) {
        if (value == null) {
            return null;
        }
        return new SpringDataTransientGetter(value);
    }
}
