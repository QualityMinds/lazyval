package scenarios.evaluation.p11.generic;

import com.qualityminds.lazyval.LazyValue;

/** A type parameter generated code would have to bind, e.g. {@code Id<Order>}, but cannot know. */
@LazyValue
public record Id<T>(String value) {}
