package scenarios.failing;

import com.qualityminds.lazyval.LazyValue;

/**
 * The shape of {@code Isbn} — private constructor, static factory — with the factory hidden too, so
 * neither route back from the payload is reachable. A factory satisfies the reconstruction rule only
 * if generated code can actually call it; being {@code static} is not enough.
 */
@LazyValue
public final class ObjectWithPrivateFactory {

    private final String value;

    private ObjectWithPrivateFactory(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    private static ObjectWithPrivateFactory of(String value) {
        return new ObjectWithPrivateFactory(value);
    }
}
