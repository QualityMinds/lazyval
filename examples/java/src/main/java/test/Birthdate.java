package test;

import com.qualityminds.lazyval.LazyValue;

import java.time.LocalDate;
import java.util.Objects;

// tag::docu[]
@LazyValue
public final class Birthdate {

    // ISO-8601 string, the canonical storage form
    private final String value;
    // derived state computed from `value` — excluded from validation by `transient`
    private final transient LocalDate parsed;

    private Birthdate(String value) {
        this.value = value;
        this.parsed = LocalDate.parse(value);
    }

    public String value() {
        return value;
    }

    public LocalDate asLocalDate() {
        return parsed;
    }

    public static Birthdate of(String isoDate) {
        Objects.requireNonNull(isoDate, "Birthdate cannot be null");
        return new Birthdate(isoDate);
    }
}
// end::docu[]
