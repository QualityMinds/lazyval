package com.qualityminds.lazyval.processor.internal

import spock.lang.Specification
import spock.lang.Title

/**
 * Pure-data unit tests for the Java-side accessor-pairing heuristic. The APT integration tests still
 * cover the wiring (element discovery, TypeMirror resolution); these tests lock down all candidate
 * filters and the matching rule itself so the contract can be enumerated independent of APT.
 */
@Title("APT accessor lookup heuristic")
class AccessorLookupSpec extends Specification {

    // ---- candidate filtering ------------------------------------------------------------

    void "non-public methods are filtered out"() {
        expect:
        AccessorLookup.accessorCandidates([
                instance("getSecret", "int", isPublic: false),
                instance("getValue", "int"),
        ])*.name() == ["getValue"]
    }

    void "static methods are filtered out"() {
        expect:
        AccessorLookup.accessorCandidates([
                instance("getValue", "int", isStatic: true),
                instance("getOther", "int"),
        ])*.name() == ["getOther"]
    }

    void "methods with parameters are filtered out"() {
        expect:
        AccessorLookup.accessorCandidates([
                instance("compute", "int", parameterCount: 1),
                instance("getValue", "int"),
        ])*.name() == ["getValue"]
    }

    void "void-returning methods are filtered out"() {
        expect:
        AccessorLookup.accessorCandidates([
                instance("doStuff", "void"),
                instance("getValue", "int"),
        ])*.name() == ["getValue"]
    }

    void "Object methods are filtered out of candidates"() {
        expect:
        AccessorLookup.accessorCandidates([
                instance("hashCode", "int"),
                instance("equals", "boolean"),
                instance("toString", "java.lang.String"),
                instance("getValue", "int"),
        ])*.name() == ["getValue"]
    }

    // ---- matching ------------------------------------------------------------------------

    void "first method whose return type matches the property type wins"() {
        given:
        def candidates = AccessorLookup.accessorCandidates([
                instance("getValue", "int"),
                instance("length", "int"),
        ])

        expect:
        AccessorLookup.findAccessor(prop("year", "int"), candidates).get().name() == "getValue"
    }

    void "order is preserved — second matching candidate is ignored"() {
        given:
        def candidates = AccessorLookup.accessorCandidates([
                instance("length", "int"),
                instance("getValue", "int"),
        ])

        expect:
        AccessorLookup.findAccessor(prop("year", "int"), candidates).get().name() == "length"
    }

    void "type mismatch returns empty"() {
        given:
        def candidates = AccessorLookup.accessorCandidates([
                instance("getValue", "java.lang.String"),
        ])

        expect:
        AccessorLookup.findAccessor(prop("year", "int"), candidates).isEmpty()
    }

    void "empty candidate list returns empty"() {
        expect:
        AccessorLookup.findAccessor(prop("year", "int"), []).isEmpty()
    }

    void "external Java type — Year-shape: field `year` paired with getValue() by type"() {
        given: 'java.time.Year exposes int getValue() and int length() in source order'
        def candidates = AccessorLookup.accessorCandidates([
                instance("getValue", "int"),
                instance("isLeap", "boolean"),
                instance("length", "int"),
        ])

        expect:
        AccessorLookup.findAccessor(prop("year", "int"), candidates).get().name() == "getValue"
    }

    void "Object hashCode override does NOT shadow the real accessor"() {
        given: 'class overrides hashCode AND declares getValue — hashCode must be filtered first'
        def candidates = AccessorLookup.accessorCandidates([
                instance("hashCode", "int"),
                instance("getValue", "int"),
        ])

        expect:
        AccessorLookup.findAccessor(prop("year", "int"), candidates).get().name() == "getValue"
    }

    /**
     * Test factory: returns an instance (non-static, public, zero-arg) method by default so
     * individual tests only declare what's interesting about the shape they're testing.
     * Note: Groovy collects named args into the FIRST parameter, so `opts` must come first.
     */
    private static AccessorLookup.Method instance(Map opts = [:], String name, String returnTypeFqn) {
        new AccessorLookup.Method(
                name,
                returnTypeFqn,
                (opts.parameterCount ?: 0) as int,
                (opts.isPublic == null ? true : opts.isPublic) as boolean,
                (opts.isStatic == null ? false : opts.isStatic) as boolean)
    }

    private static AccessorLookup.Property prop(String name, String typeFqn) {
        new AccessorLookup.Property(name, typeFqn)
    }
}
