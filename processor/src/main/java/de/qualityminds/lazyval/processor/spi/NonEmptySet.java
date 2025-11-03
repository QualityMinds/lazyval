package de.qualityminds.lazyval.processor.spi;

import java.util.*;
import java.util.stream.Stream;

public final class NonEmptySet<T> implements Iterable<T> {
    private final T first;
    private final Set<T> rest;

    private NonEmptySet(T first, Set<T> rest) {
        this.first = Objects.requireNonNull(first);
        this.rest = Set.copyOf(rest);
    }

    public static <T> NonEmptySet<T> of(T first, T... rest) {
        return new NonEmptySet<>(first, Set.of(rest));
    }

    public static <T> NonEmptySet<T> fromSet(Set<T> set) {
        if (set.isEmpty()) {
            throw new IllegalArgumentException("Set must not be empty");
        }
        var iterator = set.iterator();
        T first = iterator.next();
        Set<T> rest = new HashSet<>();
        iterator.forEachRemaining(rest::add);
        return new NonEmptySet<>(first, rest);
    }

    public T first() {
        return first;
    }

    public Set<T> toSet() {
        Set<T> result = new HashSet<>(rest);
        result.add(first);
        return Set.copyOf(result);
    }

    @Override
    public Iterator<T> iterator() {
        return toSet().iterator();
    }

    public Stream<T> stream() {
        return toSet().stream();
    }
}
