package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * The failure that needs no annotation and looks like nothing unusual: a domain-primitive wrapping
 * another value type. `value class` is not merely renamed by Kotlin, it is compiled away — the accessor
 * becomes `getMoney-cgdmosI()`, its type erases to `long`, and the constructor turns private behind a
 * synthetic marker overload.
 *
 * Left unchecked, generated Java reads the `-` as subtraction and javac reports three errors, none
 * mentioning value classes. The accessor function that rescues other unreadable payloads is no way out
 * here: a function returning a value class is mangled identically.
 *
 * Contrast `ValueClass`, where the *domain-primitive itself* is the value class.
 */
@LazyValue
class ValueClassPayload(val money: Money)

@JvmInline
value class Money(val amount: Long)
