package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * `Score` leaves its constructor public *and* offers a validating factory, which is the case that
 * proves the rule: the shim must call `Score.of`, not `Score(..)`. Both compile, so a shim that
 * preferred the constructor would pass every other test here and quietly let generated code mint
 * negative scores.
 *
 * Pinned by an approval rather than by compilation, because compilation cannot tell the two apart.
 */
@LazyValue
class ValueClassFactoryPreferred(val score: Score)

@JvmInline
value class Score(val points: Int) {

    companion object {

        fun of(points: Int): Score {
            require(points >= 0) { "Score must not be negative" }
            return Score(points)
        }
    }
}
