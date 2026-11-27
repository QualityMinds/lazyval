package com.qualityminds.lazyval.ksp.evaluation

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * AI evaluation finding T15: a Kotlin run leaves JVM-global residue behind. The IntelliJ platform code
 * embedded in the Kotlin toolchain starts a non-daemon {@code ApplicationImpl pooled thread} that is never
 * shut down, so a plain JVM (e.g. a {@code main} method running the Testkit) does not exit after a run,
 * and it sets a series of {@code idea.*}/{@code psi.*} system properties that stay set for every later test.
 * <p>
 * These tests intentionally fail on {@code main}; each feature documents one symptom of the finding. Both
 * look for the residue itself after a run rather than comparing a before/after snapshot: within one JVM
 * an earlier Kotlin run (any other IT in this module) has already left it there, which would make a
 * snapshot comparison pass for the wrong reason.
 */
class KotlinToolchainResidueIT extends Specification {

    private static final String POOLED_THREAD_PREFIX = 'ApplicationImpl pooled thread'

    /** Set by the embedded IntelliJ platform during a run, observed by the evaluation; java.awt.headless left out on purpose. */
    private static final List<String> PLATFORM_PROPERTIES = [
            'ast.loading.filter',
            'ide.enable.implicit.blocking.context',
            'ide.hide.excluded.files',
            'idea.config.path',
            'idea.ignore.disabled.plugins',
            'idea.io.use.nio2',
            'idea.plugins.compatible.build',
            'platform.random.idempotence.check.rate',
            'project.structure.add.tools.jar.to.new.jdk',
            'psi.incremental.reparse.depth.limit',
            'psi.sleep.in.validity.check',
            'psi.track.invalidation',
    ]

    @TempDir
    Path projectDir

    @Shared
    def testkitKotlin = Testkit.kotlin()

    void "T15 a Kotlin run leaves no non-daemon thread behind"() {
        when:
        def result = testkitKotlin.run(projectDir, Scenario.Kotlin.isbn())

        then: 'the run itself worked'
        result == new Testresult.Kotlin.NothingGenerated()
        and: 'nothing keeps a plain JVM from exiting once the run is over'
        lingeringPooledThreads(5_000) == []
    }

    void "T15 a Kotlin run leaves the system properties as it found them"() {
        when:
        def result = testkitKotlin.run(projectDir, Scenario.Kotlin.isbn())

        then: 'the run itself worked'
        result == new Testresult.Kotlin.NothingGenerated()
        and: 'no platform property set during the run is still set'
        PLATFORM_PROPERTIES.findAll { System.getProperty(it) != null }
                .collect { "$it=${System.getProperty(it)}".toString() } == []
    }

    /** Waits up to {@code timeoutMillis} for the pooled threads to end, in case a fix shuts the pool down asynchronously. */
    private static List<String> lingeringPooledThreads(long timeoutMillis) {
        def deadline = System.currentTimeMillis() + timeoutMillis
        List<String> lingering
        while (true) {
            lingering = Thread.allStackTraces.keySet()
                    .findAll { it.alive && !it.daemon && it.name.startsWith(POOLED_THREAD_PREFIX) }
                    .collect { "${it.name} (${it.state})".toString() }
                    .sort()
            if (lingering.isEmpty() || System.currentTimeMillis() > deadline) {
                return lingering
            }
            sleep(100)
        }
    }
}
