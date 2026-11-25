package scenarios.java;

import com.qualityminds.lazyval.LazyValue;

import java.util.Optional;

@LazyValue
public record Quantity(int value) {

    public Quantity {
        if (value < 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }
    }

    public static Quantity of(int value) {
        return new Quantity(value);
    }

    public static Optional<Quantity> of(Integer nullable) {
        return Optional.ofNullable(nullable).map(v -> of(v.intValue()));
    }
}
