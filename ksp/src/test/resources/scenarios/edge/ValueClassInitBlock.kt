package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * The counterpart to `ValueClassWithFactory`: `Celsius` validates in an `init` block instead of behind
 * a factory, so its constructor is the enforcing route and generated code may use it directly. The
 * check lands in `constructor-impl`, which is what the shim ends up calling.
 *
 * Worth pinning because "prefer the factory" must not become "always require a factory" — a value
 * class that guards its own constructor needs nothing added.
 */
@LazyValue
class ValueClassInitBlock(val temperature: Celsius)

@JvmInline
value class Celsius(val degrees: Int) {
    init {
        require(degrees >= -273) { "below absolute zero" }
    }
}
