package scenarios.springdata;

import com.qualityminds.lazyval.LazyValue;

import org.springframework.data.annotation.Transient;

/**
 * Two values, the derived one carrying Spring Data's {@code @Transient} on the field. Note the
 * package: {@code org.springframework.data.annotation}, not {@code org.springframework.data}.
 * <p>
 * The derived value is deliberately typed differently from the wrapped one: {@code AccessorLookup}
 * pairs by return type, so two same-typed values would both resolve to the first matching getter.
 */
@LazyValue
public final class SpringDataTransientField {

    private final String value;

    @Transient
    private final int derivedLength;

    private SpringDataTransientField(String value) {
        this.value = value;
        this.derivedLength = value.length();
    }

    public String getValue() {
        return value;
    }

    public int getDerivedLength() {
        return derivedLength;
    }

    public static SpringDataTransientField of(String value) {
        if (value == null) {
            return null;
        }
        return new SpringDataTransientField(value);
    }
}
