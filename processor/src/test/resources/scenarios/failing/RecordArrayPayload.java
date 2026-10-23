package scenarios.failing;

import com.qualityminds.lazyval.LazyValue;

/**
 * An array cannot carry a value: it is mutable, and a record compares the component by identity, so two
 * records over equal bytes are not equal. Binary data is what reaches for one, hence the advice to wrap a
 * String encoding of the bytes instead.
 */
@LazyValue
public record RecordArrayPayload(byte[] value) {
}
