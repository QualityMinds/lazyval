package com.qualityminds.lazyval.ksp.internal

import com.qualityminds.lazyval.ksp.spi.JavaAccessShim
import com.qualityminds.lazyval.naming.DotName
import spock.lang.Specification
import spock.lang.Title
import spock.lang.Unroll

/**
 * Unit tests for the expressions generated code uses to read a payload and rebuild it.
 *
 * The KSP integration tests still own the wiring — which member is the accessor, what its bytecode
 * name is, whether a payload is a value class — and they check these expressions only as they appear
 * in a compiled file. Here they are checked as what they are: string assembly over a plan of names,
 * so every branch and every nesting order can be enumerated without a compiler.
 *
 * A plan built by Lazyval never has an unwrapping chain without a shim, or the other way round; both
 * mean "value class payload". The two are set separately here so each branch can be reached alone.
 */
@Title("KSP payload access paths")
class AccessPlanSpec extends Specification {

    // ---- Kotlin: reading the payload ----------------------------------------------------

    void "a property payload is read as a property, an accessor payload as a call"() {
        expect:
        plan(kotlinAccessor: accessor).kotlinRead("order").asSource() == expected

        where:
        accessor  || expected
        "money"   || "order.money"
        "money()" || "order.money()"
    }

    @Unroll
    void "an unwrapping chain of #steps.size() level(s) is read outermost-first"() {
        expect:
        plan(kotlinAccessor: "value", unwrapping: steps).kotlinRead("order").asSource() == expected

        where:
        steps                                        || expected
        []                                           || "order.value"
        [property("amount")]                         || "order.value.amount"
        [property("amount"), conversion("toLong")]   || "order.value.amount.toLong()"
        [property("outer"), property("inner"),
         conversion("toLong")]                       || "order.value.outer.inner.toLong()"
    }

    void "a null-safe read of a payload carried as declared is a plain safe call"() {
        expect:
        plan(kotlinAccessor: "money").kotlinReadOrNull("order").asSource() == "order?.money"
    }

    void "a null-safe read of an unwrapped payload moves the chain inside a let"() {
        expect: 'a safe call would only guard the first hop and dereference the rest'
        plan(kotlinAccessor: "money", unwrapping: [property("amount")])
                .kotlinReadOrNull("order").asSource() == "order?.let { it.money.amount }"
    }

    // ---- Kotlin: rebuilding the domain-primitive ----------------------------------------

    void "rebuilding calls the factory where there is one, the constructor otherwise"() {
        expect:
        plan(kotlinFactory: factory).kotlinCreate("value").asSource() == expected

        where:
        factory || expected
        null    || "Order(value)"
        "of"    || "Order.of(value)"
    }

    void "a nested domain-primitive is spelled with its enclosing types, not its package"() {
        expect: 'this lands in Kotlin output, which imports the type and writes Ids.ProductId'
        plan(name: DotName.of("com.acme.order", "Ids", "ProductId"))
                .kotlinCreate("value").asSource() == "Ids.ProductId(value)"
    }

    void "reading peels the chain outermost-first and rebuilding assembles it innermost-first"() {
        given:
        def chain = plan(
                kotlinAccessor: "value",
                unwrapping: [property("outer", "com.acme.Outer"), conversion("toLong", "toULong")])

        expect:
        chain.kotlinRead("order").asSource() == "order.value.outer.toLong()"

        and: 'the same two steps, in the mirrored order'
        chain.kotlinCreate("raw").asSource() == "Order(com.acme.Outer(raw.toULong()))"
    }

    void "a null-safe rebuild is a let in either case, unwrapped or not"() {
        expect: 'Kotlin has no safe-call form for handing a nullable to a constructor'
        plan(unwrapping: steps).kotlinCreateOrNull("dbValue").asSource() == expected

        where:
        steps                                     || expected
        []                                        || "dbValue?.let { Order(it) }"
        [property("amount", "com.acme.Amount")]   || "dbValue?.let { Order(com.acme.Amount(it)) }"
    }

    // ---- Java ---------------------------------------------------------------------------

    void "Java reads through the bytecode accessor name"() {
        expect: '@JvmName and internal both move it away from the Kotlin declaration'
        plan(javaAccessorName: "getMoney\$lazyval").javaRead("order").asSource() ==
                "order.getMoney\$lazyval()"
    }

    void "Java reads through the shim when Kotlin left it no name to call"() {
        expect:
        plan(shim: shim()).javaRead("order").asSource() == "com.acme.OrderJvmAccess.read(order)"
    }

    void "Java rebuilds through #route"() {
        expect:
        plan(javaFactoryPath: factoryPath, shim: accessShim).javaCreate("value").asSource() == expected

        where:
        route             | factoryPath     | accessShim || expected
        "the constructor" | null            | null       || "new com.acme.Order(value)"
        "a JvmStatic factory" | "of"        | null       || "com.acme.Order.of(value)"
        "a companion factory" | "Companion.of" | null    || "com.acme.Order.Companion.of(value)"
        "the shim"        | null            | shim()     || "com.acme.OrderJvmAccess.create(value)"
    }

    // ---- separating the type names from the text ----------------------------------------

    void "the types an expression names come back separately, in the order they appear"() {
        when: 'a generator that manages its own imports asks for a slot instead'
        def formatted = plan(shim: shim()).javaCreate("value").asFormat('$T')

        then:
        formatted.format == '$T.create(value)'
        formatted.types*.canonicalName() == ["com.acme.OrderJvmAccess"]
    }

    void "a slot stands where the type does, so the constructor keyword stays in front of it"() {
        expect:
        plan().javaCreate("value").asFormat('$T').format == 'new $T(value)'
    }

    void "no type is named when Java can read the payload directly"() {
        when:
        def formatted = plan().javaRead("order").asFormat('$T')

        then:
        formatted.format == "order.getValue()"
        formatted.types.isEmpty()
    }

    void "a Kotlin expression names its type too, so KotlinPoet can import it"() {
        when: 'asSource() would have spelled it Order, which needs the import to already be there'
        def formatted = plan(name: DotName.of("com.acme.order", "Ids", "ProductId"))
                .kotlinCreate("value").asFormat('%T')

        then:
        formatted.format == '%T(value)'
        formatted.types*.canonicalName() == ["com.acme.order.Ids.ProductId"]
    }

    void "splicing an expression into another keeps the inner one's type separable"() {
        when: 'the rebuild sits inside a let, and the type sits inside the rebuild'
        def formatted = plan().kotlinCreateOrNull("dbValue").asFormat('%T')

        then:
        formatted.format == 'dbValue?.let { %T(it) }'
        formatted.types*.canonicalName() == ["com.acme.Order"]
    }

    void "toString is the source spelling, so an expression drops into a template"() {
        expect:
        "return ${plan().kotlinRead("order")}" == "return order.value"
    }

    // ---- helpers ------------------------------------------------------------------------

    private static AccessPlan plan(Map opts = [:]) {
        new AccessPlan(
                (opts.kotlinAccessor ?: "value") as String,
                opts.kotlinFactory as String,
                (opts.name ?: DotName.of("com.acme", "Order")) as DotName,
                (opts.javaAccessorName ?: "getValue") as String,
                opts.javaFactoryPath as String,
                (opts.unwrapping ?: []) as List<UnwrapStep>,
                opts.shim as JavaAccessShim)
    }

    private static UnwrapStep property(String name, String builder = "com.acme.Wrapper") {
        new UnwrapStep.Property(name, builder)
    }

    private static UnwrapStep conversion(String toUnderlying, String toWrapper = "toUWhatever") {
        new UnwrapStep.Conversion(toUnderlying, toWrapper)
    }

    private static JavaAccessShim shim() {
        new JavaAccessShim(DotName.of("com.acme", "OrderJvmAccess"), "read", "create")
    }
}
