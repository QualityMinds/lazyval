package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * An `internal` domain-primitive. Class names are not mangled, so the Java half of the generated
 * output can name the type, and a `public` property of an internal class keeps an unmangled
 * `getValue()` — mangling applies to internal *members*, which this one is not.
 *
 * The contrast that makes the rule worth a test: an internal *property* is rejected
 * (`failing/InternalPropertyClass`) while the internal *class* around it is fine.
 *
 * Pins the claim `rules.adoc` makes under `[#visibility]`.
 */
@LazyValue
internal class InternalDomainPrimitive(val value: String)
