package scenarios.evaluation.p11.hidden;

import com.qualityminds.lazyval.LazyValue;

/** Package-private, so its public nested type cannot be named from the package generated code is emitted into. */
class Holder {

    @LazyValue
    public record Code(String value) {}
}
