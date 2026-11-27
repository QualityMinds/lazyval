package com.qualityminds.lazyval.processor.evaluation

import com.qualityminds.lazyval.testkit.Testkit
import com.qualityminds.lazyval.testkit.Testresult
import com.qualityminds.lazyval.testkit.scenarios.Scenario
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * AI evaluation finding T1: {@code Testkit.run} starts by deleting every entry below the project directory
 * it is given ({@code Testkit.cleanProjectDir}), not only what an earlier run of the Testkit created there.
 * The Quick start in the testkit docs passes {@code java.io.tmpdir}, which turns a first test into a wipe
 * of the shared temp directory.
 * <p>
 * This test intentionally fails on {@code main}. The "shared" directory is a {@code @TempDir} holding
 * sentinel files only, so nothing real is ever deleted.
 */
class ProjectDirCleanupIT extends Specification {

    @TempDir
    Path sharedDir

    @Shared
    def testkitJava = Testkit.java()

    void "T1 running a scenario keeps files it did not create in the project dir"() {
        given: 'a directory other tools write to as well, like java.io.tmpdir in the Quick start'
        def lockFile = Files.writeString(sharedDir.resolve('other-app.lock'), 'owned by another process')
        def cacheFile = Files.createDirectories(sharedDir.resolve('ide-cache/index')).resolve('data.bin')
        Files.writeString(cacheFile, 'owned by the IDE')

        when:
        def result = testkitJava.run(sharedDir, Scenario.Java.isbn())

        then: 'the scenario ran'
        result == new Testresult.Java.NothingGenerated()
        and: 'what the Testkit did not create is still there'
        Files.exists(lockFile)
        Files.exists(cacheFile)
    }
}
