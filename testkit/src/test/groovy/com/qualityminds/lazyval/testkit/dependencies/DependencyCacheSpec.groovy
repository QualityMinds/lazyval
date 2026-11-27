package com.qualityminds.lazyval.testkit.dependencies

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/**
 * AI evaluation finding T12: {@link Dependency} caches resolved files in a static map keyed by the
 * coordinates alone and never evicts an entry. Once a test resolved a dependency from its own local repo
 * (typically a {@code @TempDir}), every later resolution in the same JVM returns that file, regardless of
 * the repo configured now, and fails as soon as the first repo has been deleted.
 * <p>
 * This test intentionally fails on {@code main}. Both local repos live in a {@code @TempDir} and already
 * contain the jar, so no download happens; the mirror is a loopback {@link HttpServer} answering 404 in
 * case anything tries to reach it.
 */
class DependencyCacheSpec extends Specification {

    @TempDir
    Path sandbox

    HttpServer mirror
    String previousRepo
    String previousMirror

    def setup() {
        previousRepo = System.getProperty(MavenResolver.PROP_RESOLVER_REPO)
        previousMirror = System.getProperty(MavenResolver.PROP_RESOLVER_MIRROR)
        mirror = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
        mirror.createContext('/', { HttpExchange exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        } as HttpHandler)
        mirror.start()
        System.setProperty(MavenResolver.PROP_RESOLVER_MIRROR, "http://127.0.0.1:${mirror.address.port}/maven2/")
    }

    def cleanup() {
        mirror?.stop(0)
        restore(MavenResolver.PROP_RESOLVER_REPO, previousRepo)
        restore(MavenResolver.PROP_RESOLVER_MIRROR, previousMirror)
    }

    void "T12 a dependency is resolved from the local repo configured now, not from the one of an earlier test"() {
        given: 'two local repos that both contain the artifact'
        def repoA = sandbox.resolve('repo-a')
        def repoB = sandbox.resolve('repo-b')
        placeJar(repoA)
        placeJar(repoB)
        def dependency = new Dependency('evaluation.t12', 'cached', '1.0')

        and: 'an earlier test resolved it from repo A'
        System.setProperty(MavenResolver.PROP_RESOLVER_REPO, repoA.toString())
        dependency.resolve()

        and: 'repo A is gone, like the @TempDir of a finished test, and repo B is configured'
        repoA.toFile().deleteDir()
        System.setProperty(MavenResolver.PROP_RESOLVER_REPO, repoB.toString())

        when:
        def resolved = dependency.resolve()

        then:
        noExceptionThrown()
        resolved.first().toPath().startsWith(repoB)
    }

    private static void placeJar(Path repo) {
        def jar = repo.resolve('evaluation/t12/cached/1.0/cached-1.0.jar')
        Files.createDirectories(jar.parent)
        def manifest = new Manifest()
        manifest.mainAttributes.put(Attributes.Name.MANIFEST_VERSION, '1.0')
        new JarOutputStream(Files.newOutputStream(jar), manifest).close()
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key)
        } else {
            System.setProperty(key, value)
        }
    }
}
