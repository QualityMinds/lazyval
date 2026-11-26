package scenarios.edge;

import com.qualityminds.lazyval.LazyValue;

import java.util.Optional;

/**
 * A {@code char} payload whose factory {@code of(char)} has a null-tolerant overload taking {@code Character}.
 * Lazyval only matches {@code of(char)}, so generated code must call that one; a {@code Character} argument would
 * bind to {@code of(Character)} instead, since javac prefers an overload that needs no unboxing.
 * <p>
 * The overload returns {@code Optional} on purpose: calling it where a {@code RecordCharFactoryOverload} is
 * expected does not compile, which turns a silent call to the wrong factory into an error a compile-only
 * test can see.
 */
@LazyValue
public record RecordCharFactoryOverload(char value) {

    public static RecordCharFactoryOverload of(char value) {
        if (!Character.isLetter(value)) {
            throw new IllegalArgumentException("Grade must be a letter");
        }
        return new RecordCharFactoryOverload(Character.toUpperCase(value));
    }

    public static Optional<RecordCharFactoryOverload> of(Character nullable) {
        return Optional.ofNullable(nullable).map(c -> of(c.charValue()));
    }
}
