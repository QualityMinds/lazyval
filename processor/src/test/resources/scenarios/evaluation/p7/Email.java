package scenarios.evaluation.p7;

import com.qualityminds.lazyval.LazyValue;

/** Gets a generated ValueExtractor, registered in META-INF/services, as soon as Bean Validation is on the classpath. */
@LazyValue
public record Email(String value) {}
