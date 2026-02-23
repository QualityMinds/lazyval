package scenarios.java;

import com.qualityminds.lazyval.LazyValue;

@LazyValue
public record Quantity(int value) {
    public Quantity {
        if (value < 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }
    }
}