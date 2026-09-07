package com.qualityminds.lazyval.naming

import spock.lang.Specification
import spock.lang.Unroll

class PayloadSpec extends Specification {

    @Unroll("#keyword boxes to #boxed and identifies as #identifier")
    void "A primitive payload carries its keyword, its boxed name and an identifier"() {
        given:
        def name = new Payload.Primitive(kind)

        expect:
        name.toString() == keyword
        name.boxed().canonicalName() == boxed
        name.identifier() == identifier
        name instanceof Payload.Primitive

        where:
        kind                       || keyword    | boxed                 | identifier
        Payload.Kind.BOOLEAN   || "boolean"  | "java.lang.Boolean"   | "Boolean"
        Payload.Kind.BYTE      || "byte"     | "java.lang.Byte"      | "Byte"
        Payload.Kind.SHORT     || "short"    | "java.lang.Short"     | "Short"
        Payload.Kind.INT       || "int"      | "java.lang.Integer"   | "Int"
        Payload.Kind.LONG      || "long"     | "java.lang.Long"      | "Long"
        Payload.Kind.CHAR      || "char"     | "java.lang.Character" | "Char"
        Payload.Kind.FLOAT     || "float"    | "java.lang.Float"     | "Float"
        Payload.Kind.DOUBLE    || "double"   | "java.lang.Double"    | "Double"
    }

    @Unroll("#canonical identifies as #identifier")
    void "A declared payload identifies by its flattened name"() {
        given:
        def name = new Payload.Declared(DotName.of(packageName, simpleNames as String[]))

        expect:
        name.toString() == canonical
        name.identifier() == identifier
        !(name instanceof Payload.Primitive)

        where:
        packageName | simpleNames          || canonical                | identifier
        "java.lang" | ["String"]           || "java.lang.String"       | "String"
        "java.time" | ["LocalDate"]        || "java.time.LocalDate"    | "LocalDate"
        "com.acme"  | ["Ids", "ProductId"] || "com.acme.Ids.ProductId" | "IdsProductId"
    }

    void "A declared payload is already a reference type, so boxing leaves it alone"() {
        given:
        def name = DotName.of("java.time", "LocalDate")

        expect:
        new Payload.Declared(name).boxed() == name
    }

    void "An identifier never contains a dot, so it is safe to build a method name from"() {
        given: "a nested payload type, whose canonical and nested spellings both carry dots"
        def name = new Payload.Declared(DotName.of("com.acme", "Ids", "ProductId"))

        expect: "mapIdsProductIdToOrder compiles, where mapIds.ProductIdToOrder would not"
        !name.identifier().contains(".")
        "map${name.identifier()}ToOrder" == "mapIdsProductIdToOrder"
    }

    @Unroll("rejects a null #part")
    void "Rejects what would make a name meaningless"() {
        when:
        build()

        then:
        thrown(NullPointerException)

        where:
        part      | build
        "kind"    | { new Payload.Primitive(null) }
        "name"    | { new Payload.Declared(null) }
    }
}
