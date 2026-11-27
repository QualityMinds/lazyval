package evaluation.k11;

import com.qualityminds.lazyval.LazyValue;

/**
 * A Java record compiled with the module's Kotlin sources and annotated directly: the same shape as the library
 * record, without the jar in between.
 */
@LazyValue
public record Seconds(long value) {
    public Seconds {
        if (value < 0) {
            throw new IllegalArgumentException("negative");
        }
    }
}
