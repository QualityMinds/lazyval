package test;

import com.qualityminds.lazyval.LazyValue;

@LazyValue
public record RecordMoreThanOneProperty(String value, String other) {
}