package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * No annotation involved at all — Kotlin's own convention is enough to move a JVM name: a `Boolean`
 * property already named `is…` keeps that name as its getter, so the bytecode has `isActive()` and no
 * `getActive()` or `getIsActive()`.
 *
 * The reason resolving the real JVM name beats spelling one from the property: a rule that only knew
 * about `@JvmName` would still get this wrong, and nothing here looks unusual enough for an author to
 * suspect Lazyval of it.
 */
@LazyValue
class BooleanIsPrefixedProperty(val isActive: Boolean)
