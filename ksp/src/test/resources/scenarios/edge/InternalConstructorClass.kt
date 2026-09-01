package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * The accepted half of the `internal` rule, and the counterpart to `failing/InternalFactoryClass`.
 *
 * With no factory, generated code reconstructs through the constructor — and a constructor is named
 * `<init>` in the bytecode, so there is no name for Kotlin to mangle a module suffix onto. The Java
 * half of the output can therefore call it, which is why the validator leaves `internal` alone here
 * while rejecting it on a factory function.
 *
 * Pins the claim `rules.adoc` makes under `[#factory]`.
 */
@LazyValue
class InternalConstructorClass internal constructor(val value: String)
