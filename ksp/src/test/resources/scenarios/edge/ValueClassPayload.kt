package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * The plain value-class payload, and the shape that needs no factory anywhere: `Money` has a public
 * constructor and a public property, and the domain-primitive is rebuilt through its own constructor.
 *
 * Java can reach none of that directly — the accessor's JVM name carries a signature hash and
 * `ValueClassPayload`'s constructor is private in the bytecode — so generated Java goes through the
 * generated `ValueClassPayloadJvmAccess`, trading in `long` because that is all `Money` erases to.
 */
@LazyValue
class ValueClassPayload(val money: Money)

@JvmInline
value class Money(val amount: Long)
