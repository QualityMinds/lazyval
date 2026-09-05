package com.qualityminds.lazyval.processor.spi

import com.qualityminds.lazyval.naming.DotName
import spock.lang.Specification

/**
 * Asserts the expressions directly, with no compiler in sight.
 *
 * That is the reason {@code AccessPlan} exists as a separate record: everything it does is string
 * assembly over a name and an accessor fragment, so it can be checked here rather than by compiling a
 * scenario and reading the generated file. The Kotlin SPI's {@code AccessPlanSpec} does the same for
 * its own, considerably harder, set of expressions.
 */
class AccessPlanSpec extends Specification {

    private static final DotName NESTED = DotName.of("com.acme.order", "Ids", "ProductId")

    void "reads the payload through the accessor the validator resolved"() {
        expect:
        plan().read("source").asSource() == "source.value()"
    }

    void "rebuilds through the constructor when there is no factory"() {
        expect:
        plan().create("v").asSource() == "new com.acme.order.Ids.ProductId(v)"
    }

    void "rebuilds through the factory when there is one"() {
        expect:
        plan(factory: "of").create("v").asSource() == "com.acme.order.Ids.ProductId.of(v)"
    }

    void "spells a type canonically in source form, so the expression needs no import"() {
        expect: "a nested type included — Ids.ProductId alone would not resolve on its own"
        plan().create("v").asSource().contains("com.acme.order.Ids.ProductId")
    }

    void "keeps the type names apart from the text, so a code writer can add the imports"() {
        when:
        def formatted = plan().create("v").asFormat('$T')

        then:
        formatted.format() == 'new $T(v)'
        formatted.types() == [NESTED]
    }

    void "names no type when only reading, so a read needs no import handling at all"() {
        when:
        def formatted = plan().read("source").asFormat('$T')

        then:
        formatted.format() == "source.value()"
        formatted.types().isEmpty()
    }

    void "toString is the source spelling, so an expression drops into a template"() {
        expect:
        "return ${plan(factory: "of").create("v")}" == "return com.acme.order.Ids.ProductId.of(v)"
    }

    // ---- helpers ------------------------------------------------------------------------

    private static AccessPlan plan(Map opts = [:]) {
        new AccessPlan(
                (opts.name ?: NESTED) as DotName,
                (opts.accessor ?: "value()") as String,
                opts.factory as String)
    }
}
