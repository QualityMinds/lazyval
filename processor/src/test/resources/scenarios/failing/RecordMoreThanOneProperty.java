package test;

import de.qualityminds.lazyval.LazyValue;

@LazyValue
public record RecordMoreThanOneProperty(String value, String other) {
}