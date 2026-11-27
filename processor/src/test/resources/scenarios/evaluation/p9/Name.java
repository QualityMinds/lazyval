package scenarios.evaluation.p9;

import com.qualityminds.lazyval.LazyValue;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code @NotBlank} also targets TYPE_USE, so the field's type is {@code java.lang.@NotBlank String} while
 * {@code value()} returns a plain {@code java.lang.String}.
 */
@LazyValue
public final class Name {

    @NotBlank
    private final String value;

    public Name(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
