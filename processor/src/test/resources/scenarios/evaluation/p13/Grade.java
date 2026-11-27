package scenarios.evaluation.p13;

import com.qualityminds.lazyval.LazyValue;

/** {@code char} has no entry in the Cassandra generator's Java-to-CQL table. */
@LazyValue
public record Grade(char value) {}
