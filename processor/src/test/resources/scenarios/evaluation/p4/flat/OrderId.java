package scenarios.evaluation.p4.flat;

import com.qualityminds.lazyval.LazyValue;

/** Same flat name as the nested {@code Order.Id}. */
@LazyValue
public record OrderId(long value) {}
