package scenarios.evaluation.p3;

import com.qualityminds.lazyval.LazyValue;

/**
 * {@code domain} is derived and has no accessor of its own, which rules.adoc calls invisible to Lazyval: it
 * needs no {@code transient}. Pairing by return type alone gives it {@code value()} as well, so the type is
 * rejected as having two payloads.
 */
@LazyValue
public final class Email {

    private final String value;
    private final String domain;

    public Email(String value) {
        this.value = value;
        this.domain = value.substring(value.indexOf('@') + 1);
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return "Email(" + value + " at " + domain + ")";
    }
}
