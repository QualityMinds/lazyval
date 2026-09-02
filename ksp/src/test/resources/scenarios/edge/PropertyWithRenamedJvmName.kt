package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * A property whose getter the author renamed. The bytecode has `payload()` and no `getValue()`, so
 * generated Java has to be told the JVM name rather than spell one from the property — which is why
 * validation resolves it instead of assuming Kotlin's JavaBean convention always holds.
 *
 * Kotlin output is unaffected: `@JvmName` moves the JVM name only, and Kotlin sources still read the
 * property as `value`.
 */
@LazyValue
class PropertyWithRenamedJvmName(@get:JvmName("payload") val value: String)
