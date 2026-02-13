package de.qualityminds.lazyval.collections;

import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Stream;

/**
 * Wrapper for any Set that ensures that it is not empty and does not contain null elements.
 * @param set the set to wrap
 * @param <T> the type of elements contained in the set
 */
public record NonEmptySet<T>(Set<T> set) implements Iterable<T> {

    /**
     * Creates a new NonEmptySet from the given set. Elements of the set must not be null.
     * @param set the set to wrap
     */
    public NonEmptySet {
        Objects.requireNonNull(set);
        if (set.isEmpty()) {
            throw new IllegalArgumentException("Collection must not be empty");
        }
        set.forEach(Objects::requireNonNull);
        set = Collections.unmodifiableSet(new LinkedHashSet<>(set));
    }

    /**
     * Retrieves the first element of the set, which depends on the underlying set implementation.
     * @return any element of the set (never null)
     */
    public T getAny() {
        return set.iterator().next();
    }

    /**
     * Returns the number of elements in this set (its cardinality).  If this
     * set contains more than {@code Integer.MAX_VALUE} elements, returns
     * {@code Integer.MAX_VALUE}.
     *
     * @return the number of elements in this set (its cardinality)
     * @see Set#size()
     */
    public int size() {
        return set.size();
    }

    /**
     * Factory method creating a NonEmptySet from an array of elements.
     * @param elements the elements to create the set from
     * @return a NonEmptySet containing the given elements
     * @param <T> the type of elements in the array
     * @throws IllegalArgumentException if the array is empty
     * @throws NullPointerException if the array is null or contains null elements
     */
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

    /**
     * Factory method creating a NonEmptySet from a single element.
     * @param element the element to create the set from
     * @return a NonEmptySet containing the given element
     * @param <T> the type of the element
     * @throws NullPointerException if the element is null
     */
    public static <T> NonEmptySet<T> of(T element) {
        Objects.requireNonNull(element);
        return new NonEmptySet<>(Set.of(element));
    }

    /**
     * Factory method converting an iterable into a NonEmptySet.
     * The Iterable must not be null, empty and must not contain null elements.
     * @param iterable the iterable to convert
     * @return a NonEmptySet containing all elements of the iterable
     * @param <T> the type of elements in the iterable
     * @throws NullPointerException if the iterable is null or contains null elements
     * @throws IllegalArgumentException if the iterable is empty
     */
    public static <T> NonEmptySet<T> ofAll(Iterable<T> iterable) {
        Objects.requireNonNull(iterable);
        Set<T> set = new LinkedHashSet<>();
        iterable.forEach(set::add);
        return new NonEmptySet<>(set);
    }

    /**
     * Since {@code NonEmptySet} is only implementing {@link Iterable},
     * this method returns the underlying set (immutable).
     * @return the underlying set
     */
    public Set<T> toSet() {
        return set;
    }

    @Override
    public Iterator<T> iterator() {
        return set.iterator();
    }

    /**
     * Forwards the stream creation to the underlying set.
     * @return a sequential {@code Stream} over the elements in this collection
     */
    public Stream<T> stream() {
        return set.stream();
    }

    /**
     * Creates a {@code Collector} that accumulates elements into a {@code NonEmptySet}.
     *
     * <pre>
     * {@code
     *   List.of("Hello", "World").stream().collect(NonEmptySet.collector())
     * }
     * </pre>
     *
     * @return Collector creating {@code NonEmptySet} from {@code Stream}
     * @param <T> the type of elements in the stream
     * @throws IllegalStateException if the stream is empty
     * @throws NullPointerException if the stream contains null elements
     */
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
