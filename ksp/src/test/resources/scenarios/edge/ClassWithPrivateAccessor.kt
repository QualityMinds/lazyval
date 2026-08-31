package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * A private function that looks exactly like a tier-1 accessor for `amount`. Generated code must
 * ignore it and fall back to the property's public JVM getter, rather than emitting a call to a
 * function it cannot reach.
 */
@LazyValue
class ClassWithPrivateAccessor(val amount: Int) {

    private fun amount(): Int = amount

    override fun toString(): String = "ClassWithPrivateAccessor(${amount()})"
}
