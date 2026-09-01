package scenarios.failing;

import com.qualityminds.lazyval.LazyValue;

/**
 * The payload is readable, but there is no way back: the only constructor is private and no factory
 * method stands in for it. Generated code lives in another package, so the reconstruction call it
 * emits cannot resolve — the mistake has to be caught here rather than by javac downstream.
 */
@LazyValue
public final class ObjectWithPrivateConstructor {

    private final String value;

    private ObjectWithPrivateConstructor(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
