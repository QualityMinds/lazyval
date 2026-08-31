package scenarios.edge;

import com.qualityminds.lazyval.LazyValue;

/**
 * A second field that generated code cannot reach must not count towards the "exactly one value"
 * rule: {@code cachedLength} has no public accessor, so {@code value} stays the unambiguous payload
 * instead of the type being rejected as a non-simple ValueType.
 */
@LazyValue
public final class ObjectWithPrivateExtraField {

    private final String value;
    private final int cachedLength;

    private ObjectWithPrivateExtraField(String value) {
        this.value = value;
        this.cachedLength = value.length();
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return "ObjectWithPrivateExtraField(" + value + ", " + cachedLength + ")";
    }

    public static ObjectWithPrivateExtraField of(String value) {
        if (value == null) {
            return null;
        }
        return new ObjectWithPrivateExtraField(value);
    }
}
