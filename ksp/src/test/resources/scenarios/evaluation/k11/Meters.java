package evaluation.k11.shared;

/**
 * A Java record from a library, listed as an external type: the record component is the payload, read through
 * the accessor {@code value()} and rebuilt through the canonical constructor, which validates.
 */
public record Meters(int value) {
    public Meters {
        if (value < 0) {
            throw new IllegalArgumentException("negative");
        }
    }
}
