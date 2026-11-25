package scenarios.java;

import com.qualityminds.lazyval.LazyValue;

/**
 * One domain-primitive per primitive payload kind, so a single compiler run proves that every arm of a
 * generator's switch over the kind produces code that compiles.
 */
public final class PrimitivePayloads {

    @LazyValue
    public record Flag(boolean value) {}

    @LazyValue
    public record Level(byte value) {}

    @LazyValue
    public record Rank(short value) {}

    @LazyValue
    public record Count(int value) {}

    @LazyValue
    public record Total(long value) {}

    @LazyValue
    public record Grade(char value) {}

    @LazyValue
    public record Ratio(float value) {}

    @LazyValue
    public record Price(double value) {}

    private PrimitivePayloads() {}
}
