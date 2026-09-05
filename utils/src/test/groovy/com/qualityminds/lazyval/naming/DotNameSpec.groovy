package com.qualityminds.lazyval.naming

import spock.lang.Specification
import spock.lang.Unroll

class DotNameSpec extends Specification {

    @Unroll("Spells #canonical")
    void "Spells a name four ways"() {
        given:
        def name = DotName.of(packageName, simpleNames as String[])

        expect:
        name.canonicalName() == canonical
        name.nestedName() == nested
        name.simpleName() == simple
        name.flatName() == flat
        name.toString() == canonical

        where:
        packageName      | simpleNames            || canonical                     | nested            | simple      | flat
        "com.acme.order" | ["Quantity"]           || "com.acme.order.Quantity"     | "Quantity"        | "Quantity"  | "Quantity"
        "com.acme.order" | ["Ids", "ProductId"]   || "com.acme.order.Ids.ProductId"| "Ids.ProductId"   | "ProductId" | "IdsProductId"
        "com.acme"       | ["A", "B", "C"]        || "com.acme.A.B.C"              | "A.B.C"           | "C"         | "ABC"
        ""               | ["Quantity"]           || "Quantity"                    | "Quantity"        | "Quantity"  | "Quantity"
        ""               | ["Ids", "ProductId"]   || "Ids.ProductId"               | "Ids.ProductId"   | "ProductId" | "IdsProductId"
    }

    void "Flattens so that two nested types of the same name generate different files"() {
        given:
        def order = DotName.of("com.acme", "Order", "Id")
        def customer = DotName.of("com.acme", "Customer", "Id")

        expect: "the simple names collide, which is why a generated name must not be derived from them"
        order.simpleName() == customer.simpleName()

        and:
        order.flatName() != customer.flatName()
    }

    void "Is a value, so it can be compared and used as a key"() {
        expect:
        DotName.of("com.acme", "Ids", "ProductId") == DotName.of("com.acme", "Ids", "ProductId")
        DotName.of("com.acme", "Ids", "ProductId") != DotName.of("com.acme", "IdsProductId")
    }

    void "Keeps its simple names unmodifiable"() {
        given:
        def names = ["Ids", "ProductId"]
        def name = new DotName("com.acme", names)

        when: "the list handed in is mutated afterwards"
        names << "Extra"

        then: "the name is unaffected"
        name.simpleNames() == ["Ids", "ProductId"]

        when:
        name.simpleNames() << "Extra"

        then:
        thrown(UnsupportedOperationException)
    }

    @Unroll("Rejects #message")
    void "Rejects a name it could not spell unambiguously"() {
        when:
        creation.call()

        then:
        thrown(expected)

        where:
        message                       | creation                                          | expected
        "null package"                | { DotName.of(null, "Quantity") }                  | NullPointerException
        "null simple name"            | { DotName.of("com.acme", null) }                  | NullPointerException
        "no simple name"              | { DotName.of("com.acme") }                        | IllegalArgumentException
        "blank simple name"           | { DotName.of("com.acme", " ") }                   | IllegalArgumentException
        "a dotted simple name"        | { DotName.of("com.acme", "Ids.ProductId") }       | IllegalArgumentException
        "a dotted enclosing name"     | { DotName.of("com.acme", "a.Ids", "ProductId") }  | IllegalArgumentException
        "null list"                   | { new DotName("com.acme", null) }                 | NullPointerException
        "empty list"                  | { new DotName("com.acme", []) }                   | IllegalArgumentException
    }
}
