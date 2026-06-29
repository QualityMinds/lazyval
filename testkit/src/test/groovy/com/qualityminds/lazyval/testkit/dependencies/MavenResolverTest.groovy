package com.qualityminds.lazyval.testkit.dependencies

import com.qualityminds.lazyval.collections.NonEmptySet
import spock.lang.Specification
import spock.lang.TempDir
import spock.util.environment.RestoreSystemProperties

import java.nio.file.Path


class MavenResolverTest extends Specification {

    @TempDir()
    Path tempLocalRepo

    // NOTE: The MavenResolver caches Dependencies statically and checks if something has gone missing.
    // Since we want to test the download, each test has to request a unique dependency

    @RestoreSystemProperties
    void "Download from default location"(){
        given:
        System.setProperty(MavenResolver.PROP_RESOLVER_REPO, tempLocalRepo.toAbsolutePath().toString())
        def dependencyToResolve = new Dependency("jakarta.annotation", "jakarta.annotation-api", "3.0.0")

        when:
        NonEmptySet<File> resolvedSet = dependencyToResolve.resolve()

        then: 'dependency was downloaded'
        !resolvedSet.isEmpty()
        and: 'cached to configured repo'
        resolvedSet.first().toPath().startsWith(tempLocalRepo)
    }

    @RestoreSystemProperties
    void "Download from configured EU mirror"(){
        given:
        System.setProperty(MavenResolver.PROP_RESOLVER_REPO, tempLocalRepo.toAbsolutePath().toString())
        System.setProperty(MavenResolver.PROP_RESOLVER_MIRROR, "https://maven-central-eu.storage-download.googleapis.com/maven2/")
        def dependencyToResolve = new Dependency("jakarta.inject", "jakarta.inject-api", "2.0.1")

        when:
        NonEmptySet<File> resolvedSet = dependencyToResolve.resolve()

        then: 'dependency was downloaded'
        !resolvedSet.isEmpty()
        and: 'cached to configured repo'
        resolvedSet.first().toPath().startsWith(tempLocalRepo)
    }

    @RestoreSystemProperties
    void "Unresolvable dependency surfaces the coordinate and URL in the exception"() {
        given: 'an artifact that does not exist on Maven Central'
        System.setProperty(MavenResolver.PROP_RESOLVER_REPO, tempLocalRepo.toAbsolutePath().toString())
        def bogus = new Dependency("com.example.lazyval.testkit.nonexistent", "bogus-artifact", "999.999.999")

        when:
        bogus.resolve()

        then: 'the exception carries the coord — not a confusing "non-empty set required"'
        def ex = thrown(IllegalStateException)
        ex.message.contains("com.example.lazyval.testkit.nonexistent:bogus-artifact:999.999.999")
        ex.message.contains("artifact not found")
        and: 'and the URL the resolver tried, so the user can verify it directly'
        ex.message.contains("bogus-artifact-999.999.999.jar")
    }

}