package scenarios.evaluation.p11.checked;

import com.qualityminds.lazyval.LazyValue;

import java.text.ParseException;

/** The factory declares a checked exception, which the generated callers neither catch nor declare. */
@LazyValue
public record Isbn(String value) {

    public static Isbn parse(String value) throws ParseException {
        if (value.isBlank()) {
            throw new ParseException(value, 0);
        }
        return new Isbn(value);
    }
}
