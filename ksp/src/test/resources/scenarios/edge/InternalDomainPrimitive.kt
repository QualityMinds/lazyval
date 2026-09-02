package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * An `internal` domain-primitive, and accepted — which looks inconsistent beside
 * `failing/InternalPropertyClass` until you ask who could exploit the leak. A caller outside the module
 * cannot name this type at all, so the public API Lazyval generates around it publishes nothing that
 * was hidden. A public type with an internal payload is the opposite: fully nameable outside, with the
 * payload handed out by every generator.
 *
 * The property here is `public` and therefore unmangled, so nothing about the JVM name is at stake
 * either way.
 *
 * Pins the claim `rules.adoc` makes under `[#visibility]`.
 */
@LazyValue
internal class InternalDomainPrimitive(val value: String)
