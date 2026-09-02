package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * The Kotlin counterpart to `PackagePrivateObject`: payload and constructor are reachable, the type
 * is not. A private top-level class is file-scoped, so generated code in another package cannot name
 * it.
 *
 * The boundary this pins down is against `edge/InternalDomainPrimitive`: `internal` is accepted because
 * generated code shares the module and an outside caller cannot name the type anyway, whereas `private`
 * is out of reach from the generated package outright. The advice has to name both.
 */
@LazyValue
private class PrivateClass(val value: String)
