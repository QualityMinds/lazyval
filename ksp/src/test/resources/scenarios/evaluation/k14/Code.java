package evaluation.k14.shared;

/** A Java type from a library exposing its payload as a public field, without an accessor method. */
public final class Code {

    public final String value;

    public Code(String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("blank");
        }
        this.value = value;
    }
}
