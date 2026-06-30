package com.qualityminds.lazyval.ksp.internal

import spock.lang.Specification
import spock.lang.Title

/**
 * Pure-data unit tests for the accessor-pairing heuristic. The KSP integration tests still cover
 * the wiring (origin detection, property/method discovery, KSType resolution); these tests lock
 * down all candidate filters and the three matching tiers so they can be enumerated independent of
 * KSP machinery.
 */
@Title("KSP accessor lookup heuristic")
class AccessorLookupSpec extends Specification {

    // ---- candidate filtering ------------------------------------------------------------

    void "static methods are filtered out"() {
        expect:
        AccessorLookup.INSTANCE.accessorCandidates([
                instance("getValue", "kotlin.Int", isStatic: true),
                instance("getOther", "kotlin.Int"),
        ])*.name == ["getOther"]
    }

    void "methods with parameters are filtered out"() {
        expect:
        AccessorLookup.INSTANCE.accessorCandidates([
                instance("compute", "kotlin.Int", parameterCount: 1),
                instance("getValue", "kotlin.Int"),
        ])*.name == ["getValue"]
    }

    void "Unit-returning methods are filtered out"() {
        expect:
        AccessorLookup.INSTANCE.accessorCandidates([
                instance("logSomething", "kotlin.Unit"),
                instance("getValue", "kotlin.Int"),
        ])*.name == ["getValue"]
    }

    void "methods without a return-type FQN are filtered out"() {
        expect:
        AccessorLookup.INSTANCE.accessorCandidates([
                instance("brokenAccessor", null),
                instance("getValue", "kotlin.Int"),
        ])*.name == ["getValue"]
    }

    void "Object methods are filtered out of candidates"() {
        expect: 'hashCode/equals/toString never become accessor candidates'
        AccessorLookup.INSTANCE.accessorCandidates([
                instance("hashCode", "kotlin.Int"),
                instance("equals", "kotlin.Boolean"),
                instance("toString", "kotlin.String"),
                instance("getValue", "kotlin.Int"),
        ])*.name == ["getValue"]
    }

    // ---- matching ------------------------------------------------------------------------

    void "tier 1 — accessor named exactly like the property wins"() {
        given:
        def candidates = AccessorLookup.INSTANCE.accessorCandidates([
                instance("value", "kotlin.String"),
                instance("toString", "kotlin.String"),
        ])

        expect:
        AccessorLookup.INSTANCE.findAccessor(prop("value", "kotlin.String"), candidates, false)?.name == "value"
    }

    void "tier 2 — JavaBean getter when name aligns with the property"() {
        given:
        def candidates = AccessorLookup.INSTANCE.accessorCandidates([instance("getValue", "kotlin.String")])

        expect:
        AccessorLookup.INSTANCE.findAccessor(prop("value", "kotlin.String"), candidates, false)?.name == "getValue"
    }

    void "tier 2 — is<Foo> form for boolean properties"() {
        given:
        def candidates = AccessorLookup.INSTANCE.accessorCandidates([instance("isActive", "kotlin.Boolean")])

        expect:
        AccessorLookup.INSTANCE.findAccessor(prop("active", "kotlin.Boolean"), candidates, false)?.name == "isActive"
    }

    void "tier 3 — type-only fallback fires for external Java types (Year-shape)"() {
        given: 'java.time.Year: field `year`, accessor `getValue()` — names differ, type matches'
        def candidates = AccessorLookup.INSTANCE.accessorCandidates([
                instance("getValue", "kotlin.Int"),
                instance("length", "kotlin.Int"),
        ])

        expect: 'first type-matching candidate is picked'
        AccessorLookup.INSTANCE.findAccessor(prop("year", "kotlin.Int"), candidates, true)?.name == "getValue"
    }

    void "tier 3 — does NOT fire for Kotlin sources, so component1 is not misidentified"() {
        given:
        def candidates = AccessorLookup.INSTANCE.accessorCandidates([instance("component1", "kotlin.Int")])

        expect:
        AccessorLookup.INSTANCE.findAccessor(prop("value", "kotlin.Int"), candidates, false) == null
    }

    void "tier order — name match beats JavaBean match even when both are present"() {
        given:
        def candidates = AccessorLookup.INSTANCE.accessorCandidates([
                instance("getValue", "kotlin.String"),
                instance("value", "kotlin.String"),
        ])

        expect:
        AccessorLookup.INSTANCE.findAccessor(prop("value", "kotlin.String"), candidates, false)?.name == "value"
    }

    void "tier order — JavaBean match beats type-only fallback for external Java types"() {
        given:
        def candidates = AccessorLookup.INSTANCE.accessorCandidates([
                instance("length", "kotlin.Int"),
                instance("getYear", "kotlin.Int"),
        ])

        expect:
        AccessorLookup.INSTANCE.findAccessor(prop("year", "kotlin.Int"), candidates, true)?.name == "getYear"
    }

    void "type mismatch on all tiers returns null"() {
        given:
        def candidates = AccessorLookup.INSTANCE.accessorCandidates([
                instance("getValue", "kotlin.String"),
        ])

        expect:
        AccessorLookup.INSTANCE.findAccessor(prop("value", "kotlin.Int"), candidates, true) == null
    }

    void "empty candidate list returns null"() {
        expect:
        AccessorLookup.INSTANCE.findAccessor(prop("value", "kotlin.String"), [], true) == null
    }

    /**
     * Test factory: returns an instance (non-static, zero-arg) method by default so individual
     * tests only declare what's interesting about the shape they're testing. Groovy collects named
     * args into the FIRST parameter, so `opts` comes first.
     */
    private static AccessorLookup.Method instance(Map opts = [:], String name, String returnTypeFqn) {
        new AccessorLookup.Method(
                name,
                returnTypeFqn,
                (opts.parameterCount ?: 0) as int,
                (opts.isStatic == null ? false : opts.isStatic) as boolean)
    }

    private static AccessorLookup.Property prop(String name, String typeFqn) {
        new AccessorLookup.Property(name, typeFqn)
    }
}
