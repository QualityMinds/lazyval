package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * Two factories with the signature Lazyval looks for, so there is no telling which one is meant to
 * rebuild the value. The same ambiguity a domain-primitive can have, reported the same way — the rule
 * is applied to the payload rather than restated for it.
 */
@LazyValue
class ValueClassAmbiguousFactory(val amount: Ambiguous)

@JvmInline
value class Ambiguous private constructor(val raw: Long) {

    companion object {

        fun of(raw: Long): Ambiguous = Ambiguous(raw)

        fun from(raw: Long): Ambiguous = Ambiguous(raw)
    }
}
