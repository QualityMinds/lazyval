package scenarios.evaluation.p4.flat;

import com.qualityminds.lazyval.LazyValue;

/** {@code Order.Id} flattens to {@code OrderId}, the name of the top-level type next to it. */
public final class Order {

    @LazyValue
    public record Id(String value) {}

    private Order() {}
}
