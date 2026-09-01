package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * The Kotlin counterpart to `PackagePrivateObject`: payload and constructor are reachable, the type
 * is not. A private top-level class is file-scoped, so generated code in another package cannot name
 * it.
 *
 * The boundary this pins down is against `edge/InternalDomainPrimitive`: `internal` is accepted
 * because class names are not mangled, `private` is not, and the advice has to name both.
 */
@LazyValue
private class PrivateClass(val value: String)
