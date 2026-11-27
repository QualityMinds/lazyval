package scenarios.evaluation.p18;

import com.qualityminds.lazyval.LazyValue;

/** {@code $} is a legal Java identifier character, but a JavaPoet format character when spliced into a format string. */
@LazyValue
public record Tag(String $name) {}
