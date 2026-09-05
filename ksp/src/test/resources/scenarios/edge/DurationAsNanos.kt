package scenarios.edge

import com.qualityminds.lazyval.LazyValue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * The way to carry a `kotlin.time.Duration`, given that `Duration` itself cannot be a payload: its
 * `rawValue` is private and packs a magnitude with a unit-discriminator bit, so there is nothing to
 * unwrap and nothing worth persisting if there were.
 *
 * The domain-primitive picks the unit instead — a decision Lazyval will not make on an author's
 * behalf, because no unit round-trips every `Duration` — and keeps `Duration` as a derived view.
 *
 * Two details are load-bearing. The `Duration` overload of `of` does not make the factory ambiguous,
 * because only the one taking the payload type is a candidate. And the view is a *function*: a
 * `val duration: Duration get() = ...` would count as a second property and be refused.
 */
@LazyValue
class DurationAsNanos private constructor(val nanos: Long) {

    companion object {

        fun of(nanos: Long): DurationAsNanos {
            require(nanos >= 0) { "A duration cannot run backwards" }
            return DurationAsNanos(nanos)
        }

        fun of(duration: Duration): DurationAsNanos = of(duration.inWholeNanoseconds)
    }

    fun asDuration(): Duration = nanos.nanoseconds
}
