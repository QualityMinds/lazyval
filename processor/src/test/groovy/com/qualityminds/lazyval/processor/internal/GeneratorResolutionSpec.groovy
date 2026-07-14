package com.qualityminds.lazyval.processor.internal

import com.qualityminds.lazyval.processor.internal.codegen.cassandra.CassandraCodecGenerator
import com.qualityminds.lazyval.processor.internal.codegen.mongo.MongoCodecGenerator
import com.qualityminds.lazyval.processor.internal.codegen.springdata.SpringDataGenerator
import com.qualityminds.lazyval.processor.spi.Generator
import com.qualityminds.lazyval.processor.spi.StockGeneratorIds
import spock.lang.Specification

class GeneratorResolutionSpec extends Specification {

    void "empty candidate set yields empty result"() {
        when:
        def result = GeneratorResolution.resolve(Set.of())

        then:
        result.active().isEmpty()
        result.superseded().isEmpty()
    }

    void "candidates without supersedes are all active"() {
        given:
        def a = gen("a")
        def b = gen("b")

        when:
        def result = GeneratorResolution.resolve(Set.of(a, b))

        then:
        result.active() == Set.of(a, b)
        result.superseded().isEmpty()
    }

    void "one generator supersedes another"() {
        given:
        def a = gen("a")
        def b = gen("b", "a")

        when:
        def result = GeneratorResolution.resolve(Set.of(a, b))

        then:
        result.active() == Set.of(b)
        result.superseded() == Set.of(superseded("a", "b"))
    }

    void "chain: only active superseders drop their targets"() {
        // a supersedes b, b supersedes c. b is dropped, so its claim on c is moot -> c survives.
        given:
        def a = gen("a", "b")
        def b = gen("b", "c")
        def c = gen("c")

        when:
        def result = GeneratorResolution.resolve(Set.of(a, b, c))

        then:
        result.active() == Set.of(a, c)
        result.superseded() == Set.of(superseded("b", "a"))
    }

    void "diamond: shared descendant survives when all its superseders are themselves dropped"() {
        // a supersedes b and c; b and c both supersede d.
        // b and c are dropped by a, so their claim on d is moot -> d survives together with a.
        given:
        def a = gen("a", "b", "c")
        def b = gen("b", "d")
        def c = gen("c", "d")
        def d = gen("d")

        when:
        def result = GeneratorResolution.resolve(Set.of(a, b, c, d))

        then:
        result.active() == Set.of(a, d)
        result.superseded() == Set.of(
                superseded("b", "a"),
                superseded("c", "a"),
        )
    }

    void "multiple active generators can supersede the same target"() {
        given:
        def x = gen("x", "z")
        def y = gen("y", "z")
        def z = gen("z")

        when:
        def result = GeneratorResolution.resolve(Set.of(x, y, z))

        then:
        result.active() == Set.of(x, y)
        result.superseded() == Set.of(
                superseded("z", "x"),
                superseded("z", "y"),
        )
    }

    void "unknown supersession target is silently ignored"() {
        given:
        def a = gen("a", "does-not-exist")

        when:
        def result = GeneratorResolution.resolve(Set.of(a))

        then:
        result.active() == Set.of(a)
        result.superseded().isEmpty()
    }

    void "unrelated generators pass through untouched"() {
        given:
        def a = gen("a", "b")
        def b = gen("b")
        def unrelated = gen("unrelated")

        when:
        def result = GeneratorResolution.resolve(Set.of(a, b, unrelated))

        then:
        result.active() == Set.of(a, unrelated)
        result.superseded() == Set.of(superseded("b", "a"))
    }

    void "cycle in supersedes graph throws"() {
        given:
        def a = gen("a", "b")
        def b = gen("b", "a")

        when:
        GeneratorResolution.resolve(Set.of(a, b))

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Cycle")
    }

    void "real generators: spring-data supersedes cassandra and mongodb codec"() {
        given:
        def springData = new SpringDataGenerator()
        def cassandra = new CassandraCodecGenerator()
        def mongo = new MongoCodecGenerator()

        when:
        def result = GeneratorResolution.resolve(Set.of(springData, cassandra, mongo))

        then:
        result.active() == Set.of(springData)
        result.superseded() == Set.of(
                superseded(StockGeneratorIds.CASSANDRA_CODEC, StockGeneratorIds.SPRING_DATA),
                superseded(StockGeneratorIds.MONGODB_CODEC, StockGeneratorIds.SPRING_DATA),
        )
    }

    private Generator gen(String id, String... targets) {
        Mock(Generator) {
            generatorId() >> id
            supersedes() >> (targets as Set)
        }
    }

    private static GeneratorResolution.Superseded superseded(String id, String by) {
        new GeneratorResolution.Superseded(id, by)
    }
}
