package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * The visibility that looks like it should work: `internal` is visible to the whole module, generated
 * sources included. What breaks it is the JVM name — Kotlin mangles an internal property's getter to
 * `getValue$module`, and the Java half of Lazyval's output finds no `getValue()` to call, which is why
 * the message spells the mangling out instead of just saying "not public".
 *
 * Contrast `edge/InternalDomainPrimitive`, where an internal *class* is accepted: class names are not
 * mangled.
 */
@LazyValue
class InternalPropertyClass(internal val value: String)
