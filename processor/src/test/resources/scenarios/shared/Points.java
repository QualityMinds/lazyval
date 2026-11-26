package scenarios.shared;

/**
 * A domain-primitive from a shared Java module, compiled as the scenario's library and listed as an external
 * type. It holds its payload boxed, which Kotlin sees as {@code Int}, just as it sees an {@code int}.
 * <p>
 * The factory Lazyval matches takes an {@code Integer}: given an {@code int} instead, javac would bind
 * {@code of(long)}, by widening. That overload declares a checked exception, so generated Java bound to it
 * does not compile, which turns a silent call to the wrong factory into an error a compile-only test can see.
 */
public final class Points {

    private final Integer value;

    private Points(Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }

    public static Points of(Integer value) {
        return new Points(value);
    }

    public static Points of(long value) throws Exception {
        return new Points(Math.toIntExact(value));
    }
}
