package scenarios.lettuce;

import com.qualityminds.lazyval.LazyValue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * One domain-primitive per kind of payload the Lettuce generator encodes, so a single compiler run
 * proves every branch of its text table produces code that compiles.
 */
public final class LettucePayloads {

    public enum Priority { LOW, HIGH }

    @LazyValue
    public record Flag(boolean value) {}

    @LazyValue
    public record Counter(long value) {}

    @LazyValue
    public record Price(BigDecimal value) {}

    @LazyValue
    public record Tracking(UUID value) {}

    @LazyValue
    public record Moment(Instant value) {}

    @LazyValue
    public record Zone(ZoneId value) {}

    @LazyValue
    public record Legacy(Date value) {}

    @LazyValue
    public record Level(Priority value) {}

    @LazyValue
    public record Blob(byte[] value) {}

    private LettucePayloads() {}
}
