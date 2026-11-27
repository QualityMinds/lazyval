package scenarios.evaluation.p10;

import com.qualityminds.lazyval.LazyValue;

/** Processed in round 1, next to a domain-primitive another processor generates in that round. */
@LazyValue
public record Email(String value) {}
