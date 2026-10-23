package scenarios.failing;

import com.qualityminds.lazyval.LazyValue;

/**
 * The class route reaches the same rule as {@code RecordArrayPayload}, and so does an array of anything
 * other than bytes.
 */
@LazyValue
public final class ObjectArrayPayload {

    private final String[] value;

    public ObjectArrayPayload(String[] value) {
        this.value = value;
    }

    public String[] value() {
        return value;
    }
}
