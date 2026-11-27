package evaluation.k8

import com.qualityminds.lazyval.LazyValue

/** A common idiom: companions share a validating factory through a base class. */
abstract class ValidatingFactory<T>(private val create: (String) -> T, private val pattern: Regex) {
    fun of(value: String): T {
        require(pattern.matches(value)) { "invalid: $value" }
        return create(value)
    }
}

/**
 * `OrderId.of(...)` is the factory: inherited by the companion, so it is not among the companion's declared
 * functions. Generated code that calls the public constructor instead skips the pattern check.
 */
@LazyValue
class OrderId(val value: String) {
    companion object : ValidatingFactory<OrderId>(::OrderId, Regex("ORD-[0-9]{6}"))
}

/**
 * The same idiom with the constructor restricted to the module, which says even more plainly that `of` is the
 * way in.
 */
@LazyValue
class InvoiceId internal constructor(val value: String) {
    companion object : ValidatingFactory<InvoiceId>(::InvoiceId, Regex("INV-[0-9]{6}"))
}
