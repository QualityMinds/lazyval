package scenarios.evaluation.p4.b;

import com.qualityminds.lazyval.LazyValue;

/** Shares its simple name with {@code scenarios.evaluation.p4.a.Email}, as two bounded contexts may. */
@LazyValue
public record Email(String value) {}
