package scenarios.failing;

import com.qualityminds.lazyval.LazyValue;

/**
 * The only method that looks like an accessor for {@code value} is private, so generated code — which
 * lives in another package — has no way to read the payload. Unlike Kotlin, a private Java field has
 * no synthesized public getter to fall back on, so the type has to be rejected rather than paired.
 */
@LazyValue
public final class ObjectWithPrivateAccessor {

    private final String value;

    public ObjectWithPrivateAccessor(String value) {
        this.value = value;
    }

    private String value() {
        return value;
    }

    @Override
    public String toString() {
        return "ObjectWithPrivateAccessor(" + value() + ")";
    }
}
