package scenarios.evaluation.p13;

import com.qualityminds.lazyval.LazyValue;

/** An enum has no entry in the Cassandra generator's Java-to-CQL table. */
@LazyValue
public record Level(Severity value) {}
