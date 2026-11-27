package com.qualityminds.lazyval.ksp.evaluation

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.dependencies.Dependency
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title
import spock.lang.Unroll

import java.nio.file.Path

/**
 * AI evaluation finding K21 (GitHub issue "Evaluation: ksp", label evaluation). This specification intentionally
 * fails on main: it documents the expected behaviour, not the current one.
 * <p>
 * With user codecs configured, the generated codecs file detects which generated codec a user codec overrides. The
 * set of generated types is written as raw {@code SimpleName::class.java} strings
 * ({@code CassandraCodecGenerator.buildOverrideDetectionBlock}, {@code MongoCodecGenerator.buildOverrideDetectionInit})
 * rather than through KotlinPoet's type names, so a nested domain-primitive is referred to as {@code ProductId}
 * instead of {@code Ids.ProductId}. On main kotlinc fails with "Unresolved reference 'ProductId'".
 */
@Title("Evaluation K21 - codec override detection for nested domain-primitives")
class K21CodecOverrideDetectionIT extends Specification {

    static final Dependency BSON = new Dependency("org.mongodb", "bson", "5.6.5")
    static final Dependency CASSANDRA_DRIVER_CORE = new Dependency("com.datastax.oss", "java-driver-core", "4.17.0")

    @TempDir
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    @Unroll("K21 #generatedFile with a user codec in #option compiles for a nested domain-primitive")
    void "K21 user codecs next to a nested domain-primitive produce a codecs file that compiles"() {
        given:
        def scenario = Scenario.Kotlin.of("nested Ids.ProductId and $userCodec",
                "scenarios/evaluation/k21/Ids.kt",
                "scenarios/evaluation/k21/${userCodec}.kt")
                .withDependencies(dependency)
                .withOption(option, "evaluation.k21.$userCodec")

        when:
        def result = testkitKotlin.run(projectDir, scenario)

        then:
        result == new Testresult.Kotlin.Success(generatedFile)

        where:
        option                     | userCodec            | dependency            | generatedFile
        'lazyval.mongodb.codecs'   | 'UserMongoCodec'     | BSON                  | 'LazyvalMongoCodecs.kt'
        'lazyval.cassandra.codecs' | 'UserCassandraCodec' | CASSANDRA_DRIVER_CORE | 'LazyvalCassandraCodecs.kt'
    }
}
