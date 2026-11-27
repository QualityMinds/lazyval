package com.qualityminds.lazyval.testkit.dependencies

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/**
 * AI evaluation finding T2: {@link MavenResolver} stores the body of any response {@code HttpURLConnection}
 * does not turn into an exception (every 2xx, and every 3xx it does not follow) as the jar. It checks neither
 * the status itself nor the published checksum nor whether the body is a zip at all, and it trusts any
 * non-empty file already at the jar's path in the local repo.
 * <p>
 * These tests intentionally fail on {@code main}; each feature documents one symptom of the finding.
 * The local repo is a {@code @TempDir} and the mirror a loopback {@link HttpServer}, so nothing touches
 * {@code ~/.m2} or the network. Each feature resolves its own coordinates because {@link Dependency}
 * caches resolutions for the lifetime of the JVM.
 */
class MavenResolverDownloadValidationSpec extends Specification {

    private static final byte[] HTML_PAGE =
            '<html><body>Please log in to the corporate proxy</body></html>'.getBytes(StandardCharsets.UTF_8)

    @TempDir
    Path localRepo

    HttpServer mirror
    volatile Closure<Void> answer
    String previousRepo
    String previousMirror

    def setup() {
        previousRepo = System.getProperty(MavenResolver.PROP_RESOLVER_REPO)
        previousMirror = System.getProperty(MavenResolver.PROP_RESOLVER_MIRROR)
        mirror = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
        mirror.createContext('/', { HttpExchange exchange -> answer.call(exchange) } as HttpHandler)
        mirror.start()
        System.setProperty(MavenResolver.PROP_RESOLVER_REPO, localRepo.toString())
        System.setProperty(MavenResolver.PROP_RESOLVER_MIRROR, "http://127.0.0.1:${mirror.address.port}/maven2/")
    }

    def cleanup() {
        mirror?.stop(0)
        restore(MavenResolver.PROP_RESOLVER_REPO, previousRepo)
        restore(MavenResolver.PROP_RESOLVER_MIRROR, previousMirror)
    }

    void "T2 a 200 response carrying an HTML page is not stored as the jar"() {
        given: 'a captive portal answering every request with a login page'
        answer = { HttpExchange exchange -> send(exchange, 200, HTML_PAGE, ['Content-Type': 'text/html']) }

        when:
        new Dependency('evaluation.t2', 'html-page', '1.0').resolve()

        then:
        thrown(IllegalStateException)
        and: 'nothing is left in the local repo for the coordinates'
        filesIn(versionDir('evaluation.t2', 'html-page', '1.0')).isEmpty()
    }

    void "T2 a redirect to another protocol is not stored as the jar"() {
        given: 'a mirror moved to https, which HttpURLConnection does not follow'
        answer = { HttpExchange exchange ->
            def target = "https://127.0.0.1:${mirror.address.port}${exchange.requestURI.rawPath}"
            send(exchange, 302, '<html><body>302 Found - moved to https</body></html>'.getBytes(StandardCharsets.UTF_8),
                    ['Location': target, 'Content-Type': 'text/html'])
        }

        when:
        new Dependency('evaluation.t2', 'moved-to-https', '1.0').resolve()

        then:
        thrown(IllegalStateException)
        and: 'nothing is left in the local repo for the coordinates'
        filesIn(versionDir('evaluation.t2', 'moved-to-https', '1.0')).isEmpty()
    }

    void "T2 a jar that does not match its published sha1 is rejected"() {
        given: 'a mirror serving a jar together with the checksum of different content'
        def jar = jarBytes()
        answer = { HttpExchange exchange ->
            def path = exchange.requestURI.rawPath
            if (path.endsWith('.jar')) {
                send(exchange, 200, jar)
            } else if (path.endsWith('.jar.sha1')) {
                send(exchange, 200, sha1('some other content'.bytes).getBytes(StandardCharsets.US_ASCII))
            } else {
                send(exchange, 404, new byte[0])
            }
        }

        when:
        new Dependency('evaluation.t2', 'checksum-mismatch', '1.0').resolve()

        then:
        thrown(IllegalStateException)
        and: 'nothing is left in the local repo for the coordinates'
        filesIn(versionDir('evaluation.t2', 'checksum-mismatch', '1.0')).isEmpty()
    }

    void "T2 a corrupt file already in the local repo is not returned as-is"() {
        given: 'an earlier poisoned download sits where the jar belongs'
        def existing = versionDir('evaluation.t2', 'poisoned', '1.0').resolve('poisoned-1.0.jar')
        Files.createDirectories(existing.parent)
        Files.write(existing, HTML_PAGE)
        and: 'the mirror would serve the real jar with a matching checksum'
        def jar = jarBytes()
        answer = { HttpExchange exchange ->
            def path = exchange.requestURI.rawPath
            if (path.endsWith('.jar')) {
                send(exchange, 200, jar)
            } else if (path.endsWith('.jar.sha1')) {
                send(exchange, 200, sha1(jar).getBytes(StandardCharsets.US_ASCII))
            } else {
                send(exchange, 404, new byte[0])
            }
        }

        when:
        def outcome = resolveOrFailure(new Dependency('evaluation.t2', 'poisoned', '1.0'))

        then: 'the corrupt file is either rejected or replaced by the jar from the mirror'
        outcome instanceof IllegalStateException || isReadableJar(outcome.first())
    }

    private static Object resolveOrFailure(Dependency dependency) {
        try {
            return dependency.resolve()
        } catch (IllegalStateException e) {
            return e
        }
    }

    private Path versionDir(String groupId, String artifactId, String version) {
        localRepo.resolve(groupId.replace('.', '/')).resolve(artifactId).resolve(version)
    }

    private static List<String> filesIn(Path dir) {
        if (!Files.isDirectory(dir)) {
            return []
        }
        Files.list(dir).withCloseable { stream -> stream.map { it.fileName.toString() }.toList() }
    }

    private static boolean isReadableJar(File file) {
        try {
            new JarFile(file).withCloseable { it.entries().hasMoreElements() }
        } catch (IOException ignored) {
            false
        }
    }

    private static byte[] jarBytes() {
        def bytes = new ByteArrayOutputStream()
        def manifest = new Manifest()
        manifest.mainAttributes.put(Attributes.Name.MANIFEST_VERSION, '1.0')
        new JarOutputStream(bytes, manifest).withCloseable { jar ->
            jar.putNextEntry(new JarEntry('evaluation/marker.txt'))
            jar.write('marker'.getBytes(StandardCharsets.UTF_8))
            jar.closeEntry()
        }
        bytes.toByteArray()
    }

    private static String sha1(byte[] content) {
        MessageDigest.getInstance('SHA-1').digest(content).encodeHex().toString()
    }

    private static void send(HttpExchange exchange, int status, byte[] body, Map<String, String> headers = [:]) {
        headers.each { name, value -> exchange.responseHeaders.add(name, value) }
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length)
        if (body.length > 0) {
            exchange.responseBody.withCloseable { it.write(body) }
        }
        exchange.close()
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key)
        } else {
            System.setProperty(key, value)
        }
    }
}
