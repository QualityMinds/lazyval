package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * An `internal` constructor, and the case that needs nothing added: a constructor is `<init>` in the
 * bytecode, so there is no name for Kotlin to mangle a module suffix onto and generated Java can call it
 * as it stands.
 *
 * The factory counterpart is `InternalFactoryWithJvmName`, which wants the same thing — construction
 * restricted to the declaring module — and needs `@JvmName` only because a function has a name.
 *
 * Pins the claim `rules.adoc` makes under `[#factory]`.
 */
@LazyValue
class InternalConstructorClass internal constructor(val value: String)
