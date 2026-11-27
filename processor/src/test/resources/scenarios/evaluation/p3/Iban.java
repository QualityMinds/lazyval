package scenarios.evaluation.p3;

import com.qualityminds.lazyval.LazyValue;

/**
 * The payload is {@code value}, exposed by {@code value()}. {@code masked()} returns the same type and is
 * declared first, so pairing the field with the first method of a matching return type persists the masked
 * display form instead of the payload.
 */
@LazyValue
public final class Iban {

    private final String value;

    private Iban(String value) {
        this.value = value;
    }

    public static Iban of(String raw) {
        if (raw == null || !raw.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}")) {
            throw new IllegalArgumentException("not an IBAN");
        }
        return new Iban(raw);
    }

    /** For display and logging: everything but the last four characters masked. */
    public String masked() {
        return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
    }

    public String value() {
        return value;
    }
}
