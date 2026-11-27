package scenarios.evaluation.p11.collection;

import com.qualityminds.lazyval.LazyValue;

import java.util.List;

/** A parameterized payload type, which has no class literal of its own. */
@LazyValue
public record Tags(List<String> value) {}
