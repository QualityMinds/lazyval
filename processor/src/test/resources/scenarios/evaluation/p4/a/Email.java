package scenarios.evaluation.p4.a;

import com.qualityminds.lazyval.LazyValue;

/** Shares its simple name with {@code scenarios.evaluation.p4.b.Email}, as two bounded contexts may. */
@LazyValue
public record Email(String value) {}
