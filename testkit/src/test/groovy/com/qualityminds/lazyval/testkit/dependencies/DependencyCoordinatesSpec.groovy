package com.qualityminds.lazyval.testkit.dependencies

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * AI evaluation finding T16: {@link Dependency} and {@link MavenResolver} use the coordinates verbatim,
 * both as path segments below the local repo and as part of the download URL. A {@code ..} segment
 * writes the jar outside the configured repo, and URL syntax in a coordinate changes the request.
 * <p>
 * These tests intentionally fail on {@code main}; each feature documents one symptom of the finding.
 * The local repo sits inside a {@code @TempDir} sandbox, so a file escaping the repo lands in the
 * sandbox and nowhere else; the mirror is a loopback {@link HttpServer} answering 200 to everything.
 */
class DependencyCoordinatesSpec extends Specification {

    @TempDir
    Path sandbox

    Path localRepo
    HttpServer mirror
    List<String> requests = Collections.synchronizedList([])
    String previousRepo
    String previousMirror

    def setup() {
        previousRepo = System.getProperty(MavenResolver.PROP_RESOLVER_REPO)
        previousMirror = System.getProperty(MavenResolver.PROP_RESOLVER_MIRROR)
        localRepo = Files.createDirectories(sandbox.resolve('m2'))
        mirror = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
        mirror.createContext('/', { HttpExchange exchange ->
            requests << exchange.requestURI.toString()
            def body = 'PK\u0003\u0004 served'.getBytes(StandardCharsets.ISO_8859_1)
            exchange.sendResponseHeaders(200, body.length)
            exchange.responseBody.withCloseable { it.write(body) }
            exchange.close()
        } as HttpHandler)
        mirror.start()
        System.setProperty(MavenResolver.PROP_RESOLVER_REPO, localRepo.toString())
        System.setProperty(MavenResolver.PROP_RESOLVER_MIRROR, "http://127.0.0.1:${mirror.address.port}/maven2/")
    }

    def cleanup() {
        mirror?.stop(0)
        restore(MavenResolver.PROP_RESOLVER_REPO, previousRepo)
        restore(MavenResolver.PROP_RESOLVER_MIRROR, previousMirror)
    }

    void "T16 a path-traversing coordinate is rejected before anything is written outside the local repo"() {
        given: 'the group directory exists, as it does in any real local repo'
        Files.createDirectories(localRepo.resolve('org'))

        when:
        def outcome = resolveOrFailure('org', '..', '..')

        then: 'nothing appeared next to the local repo'
        entries(sandbox) == ['m2']
        and: 'the coordinates were rejected'
        outcome instanceof IllegalArgumentException || outcome instanceof IllegalStateException
    }

    void "T16 a version carrying URL syntax is rejected instead of being spliced into the download URL"() {
        when:
        def outcome = resolveOrFailure('evaluation.t16', 'query', '1.0?x=#frag')

        then: 'the mirror was never asked for a URL the coordinates do not describe'
        requests.isEmpty()
        and: 'the coordinates were rejected'
        outcome instanceof IllegalArgumentException || outcome instanceof IllegalStateException
    }

    private static Object resolveOrFailure(String groupId, String artifactId, String version) {
        try {
            return new Dependency(groupId, artifactId, version).resolve()
        } catch (RuntimeException e) {
            return e
        }
    }

    private static List<String> entries(Path dir) {
        Files.list(dir).withCloseable { stream -> stream.map { it.fileName.toString() }.sorted().toList() }
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key)
        } else {
            System.setProperty(key, value)
        }
    }
}
