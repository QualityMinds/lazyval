package de.qualityminds.lazyval.collections;

import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Stream;

public record NonEmptySet<T>(Set<T> set) implements Iterable<T> {

    public NonEmptySet {
        Objects.requireNonNull(set);
        if (set.isEmpty()) {
            throw new IllegalArgumentException("Collection must not be empty");
        }
        set.forEach(Objects::requireNonNull);
        set = Collections.unmodifiableSet(new LinkedHashSet<>(set));
    }

    /**
     * Since Set has no guaranteed order, this method returns any element of the set.
     */
    public T getAny() {
        return set.iterator().next();
    }

    @SafeVarargs
    public static <T> NonEmptySet<T> of(T... elements) {
        Objects.requireNonNull(elements);
        if (elements.length == 0) {
            throw new IllegalArgumentException("Set must not be empty");
        }
        if (elements.length == 1) {
            return of(elements[0]);
        } else {
            return new NonEmptySet<>(new LinkedHashSet<>(Arrays.asList(elements)));
        }
    }

    public static <T> NonEmptySet<T> of(T element) {
        return new NonEmptySet<>(Set.of(element));
    }

    public static <T> NonEmptySet<T> ofAll(Iterable<T> iterable) {
        Objects.requireNonNull(iterable);
        Set<T> set = new LinkedHashSet<>();
        iterable.forEach(set::add);
        return new NonEmptySet<>(set);
    }


    public Set<T> toSet() {
        return set;
    }

    @Override
    public Iterator<T> iterator() {
        return set.iterator();
    }

    public Stream<T> stream() {
        return set.stream();
    }

    public static <T> Collector<T, ?, NonEmptySet<T>> collector() {
        return Collector.of(
                () -> new ArrayList<T>(),
                ArrayList::add,
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                accumulated -> {
                    if (accumulated.isEmpty()) {
                        throw new IllegalStateException("Cannot create NonEmptySet from empty stream");
                    }
                    return NonEmptySet.ofAll(accumulated);
                }
        );
    }
}
