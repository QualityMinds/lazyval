package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * The same rejection with the technical objection removed: `@get:JvmName` suppresses the mangling, so
 * the getter sits in the bytecode as a plain `getValue()` that generated Java could call.
 *
 * It is still refused, and that is the point of the scenario — reachability was never the reason. An
 * annotation cannot buy an internal payload, because what the rule protects is the author's own
 * decision not to publish it, which `@JvmName` says nothing about.
 */
@LazyValue
class InternalPropertyWithJvmName(@get:JvmName("getValue") internal val value: String)
