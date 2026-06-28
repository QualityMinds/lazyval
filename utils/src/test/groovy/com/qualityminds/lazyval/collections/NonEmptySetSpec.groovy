package com.qualityminds.lazyval.collections


import spock.lang.Specification
import spock.lang.Unroll

import static java.util.Collections.emptyList
import static java.util.Collections.emptySet

class NonEmptySetSpec extends Specification {

    @Unroll("Invariants #message")
    void "Invariants"(){
        when:
        creationClosure.call()

        then:
        thrown(expectedException)

        where:
        message                          | creationClosure                          | expectedException
        "factory: nulls"                 | { NonEmptySet.of(null, null) }           | NullPointerException
        "factory: null single element"   | { NonEmptySet.of(null) }                 | NullPointerException
        "factory iterable: null"         | { NonEmptySet.ofAll(null) }              | NullPointerException
        "factory array: empty-array"     | { NonEmptySet.of(new String[0]) }        | IllegalArgumentException
        "factory array: null-varargs"    | { NonEmptySet.of(null, null) }           | NullPointerException
        "factory iterable: empty-list"   | { NonEmptySet.ofAll(emptyList()) }       | IllegalArgumentException
        "constructor: null"              | { new NonEmptySet(null) }                | NullPointerException
        "constructor: empty-set"         | { new NonEmptySet(emptySet()) }          | IllegalArgumentException
    }

    void "created from single element"(){
        expect:
        NonEmptySet.of("Hello").getAny() == "Hello"
    }

    void "created from collection"(){
        given:
        def elements = List.of("Hello", "World")

        when:
        def set = NonEmptySet.ofAll(elements)

        then:
        set ==~ elements
    }

    void "created from varargs"(){
        when:
        def set = NonEmptySet.of("Hello", "World")

        then:
        set ==~ ["Hello", "World"]
    }

    void "stream collector"() {
        when:
        def streamedSet = List.of("Hello", "World").stream().collect(NonEmptySet.collector())

        then:
        streamedSet ==~ ["Hello", "World"]

        and:
        streamedSet instanceof NonEmptySet
    }

    void "insertion order is preserved across every construction and iteration path"() {
        given: 'a deliberately non-alphabetical input so we can tell preserved order from accidental sorting'
        def insertionOrder = ["zebra", "apple", "mango", "banana"]

        expect: 'varargs factory iterates in the order arguments were given'
        NonEmptySet.of("zebra", "apple", "mango", "banana").stream().toList() == insertionOrder

        and: 'iterable factory iterates in the order of the source iterable'
        NonEmptySet.ofAll(insertionOrder).stream().toList() == insertionOrder

        and: 'stream collector preserves stream encounter order'
        insertionOrder.stream().collect(NonEmptySet.collector()).stream().toList() == insertionOrder

        and: 'iterator() returns elements in insertion order'
        def fromIterator = []
        NonEmptySet.ofAll(insertionOrder).iterator().forEachRemaining { fromIterator << it }
        fromIterator == insertionOrder

        and: 'forEach (Iterable contract) visits elements in insertion order'
        def fromForEach = []
        NonEmptySet.ofAll(insertionOrder).forEach { fromForEach << it }
        fromForEach == insertionOrder

        and: 'the underlying Set view also preserves insertion order'
        new ArrayList<String>(NonEmptySet.ofAll(insertionOrder).toSet()) == insertionOrder
    }

}