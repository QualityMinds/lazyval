package scenarios.failing;

import com.qualityminds.lazyval.LazyValue;

/**
 * Payload, accessor and constructor are all reachable — the type around them is not. Generated code
 * is emitted into its own package, so a package-private domain-primitive cannot even be named there,
 * let alone constructed.
 *
 * Carries a generator on the classpath so the run gets far enough to generate: the point of the rule
 * is that validation rejects the type before that happens.
 */
@LazyValue
final class PackagePrivateObject {

    private final String value;

    public PackagePrivateObject(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
