package evaluation.k5.shared;

/**
 * A Java value type from a library, listed as an external type. The field and its accessor are named
 * differently, as in {@code java.time.Year} (field {@code year}, accessor {@code getValue()}), so neither the
 * name match nor the JavaBean match finds the accessor and the type-only fallback decides.
 * <p>
 * A derived view of the same type is declared before the accessor: picking the first {@code double} getter
 * persists {@code asFahrenheit()}, and {@link #of(double)} then reads it back as Celsius.
 */
public final class Celsius {

    private final double degrees;

    private Celsius(double degrees) {
        this.degrees = degrees;
    }

    public static Celsius of(double degrees) {
        return new Celsius(degrees);
    }

    /** Derived view, same type as the field. */
    public double asFahrenheit() {
        return degrees * 9 / 5 + 32;
    }

    /** The accessor: what {@link #of(double)} takes back. */
    public double celsius() {
        return degrees;
    }
}
