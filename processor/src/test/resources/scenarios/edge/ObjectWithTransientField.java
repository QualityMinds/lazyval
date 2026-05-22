package scenarios.edge;

import com.qualityminds.lazyval.LazyValue;

@LazyValue
public final class ObjectWithTransientField {

    private final String value;
    private final transient int derivedYear;

    private ObjectWithTransientField(String value) {
        this.value = value;
        this.derivedYear = Integer.parseInt(value.substring(0, 4));
    }

    public String value() {
        return value;
    }

    public int getDerivedYear() {
        return derivedYear;
    }

    public static ObjectWithTransientField of(String value) {
        if (value == null) {
            return null;
        }
        return new ObjectWithTransientField(value);
    }
}
