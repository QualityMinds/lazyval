package scenarios.evaluation.p21;

import com.qualityminds.lazyval.LazyValue;

/** Its nested types belong to the current compilation unit, just as a top-level type does. */
public final class Outer {

    @LazyValue
    public record Code(String value) {}

    public record Plain(String value) {}

    private Outer() {}
}
