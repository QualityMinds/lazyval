package com.qualityminds.lazyval.testkit.dependencies

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/**
 * AI evaluation finding T5: {@link MavenResolver} downloads into a temp file with a fixed name,
 * {@code <artifact>.jar.tmp}, next to the final jar. Two JVMs sharing a local repo (parallel builds, a
 * second module's tests, an IDE run next to Maven) therefore write into the same file: the second one
 * truncates the first one's half-finished download and renames it away under its feet.
 * <p>
 * This test intentionally fails on {@code main}. The other JVM is simulated deterministically by a plain
 * writer holding the {@code .tmp} file open with half of the jar written, the way a concurrent download
 * using the same naming scheme would. The local repo is a {@code @TempDir}, the mirror a loopback
 * {@link HttpServer}.
 */
class MavenResolverTempFileSpec extends Specification {

    @TempDir
    Path localRepo

    HttpServer mirror
    FileChannel otherJvm
    String previousRepo
    String previousMirror

    def setup() {
        previousRepo = System.getProperty(MavenResolver.PROP_RESOLVER_REPO)
        previousMirror = System.getProperty(MavenResolver.PROP_RESOLVER_MIRROR)
        mirror = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
        mirror.createContext('/', { HttpExchange exchange ->
            if (exchange.requestURI.rawPath.endsWith('.jar')) {
                exchange.sendResponseHeaders(200, JAR.length)
                exchange.responseBody.withCloseable { it.write(JAR) }
            } else {
                exchange.sendResponseHeaders(404, -1)
            }
            exchange.close()
        } as HttpHandler)
        mirror.start()
        System.setProperty(MavenResolver.PROP_RESOLVER_REPO, localRepo.toString())
        System.setProperty(MavenResolver.PROP_RESOLVER_MIRROR, "http://127.0.0.1:${mirror.address.port}/maven2/")
    }

    def cleanup() {
        otherJvm?.close()
        mirror?.stop(0)
        restore(MavenResolver.PROP_RESOLVER_REPO, previousRepo)
        restore(MavenResolver.PROP_RESOLVER_MIRROR, previousMirror)
    }

    void "T5 a download leaves another JVM's in-progress download of the same artifact alone"() {
        given: 'another JVM is half-way through downloading the artifact into the shared local repo'
        def otherDownload = localRepo.resolve('evaluation/t5/in-flight/1.0/in-flight-1.0.jar.tmp')
        Files.createDirectories(otherDownload.parent)
        byte[] firstHalf = Arrays.copyOf(JAR, JAR.length.intdiv(2) as int)
        otherJvm = FileChannel.open(otherDownload, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        otherJvm.write(ByteBuffer.wrap(firstHalf))

        when: 'this JVM resolves the same artifact meanwhile'
        def resolved = new Dependency('evaluation.t5', 'in-flight', '1.0').resolve()

        then: "the other JVM's partial download is neither truncated nor moved away"
        Files.exists(otherDownload)
        Files.readAllBytes(otherDownload) == firstHalf
        and: 'this JVM got the complete jar'
        Files.readAllBytes(resolved.first().toPath()) == JAR
    }

    private static final byte[] JAR = jarBytes()

    private static byte[] jarBytes() {
        def bytes = new ByteArrayOutputStream()
        def manifest = new Manifest()
        manifest.mainAttributes.put(Attributes.Name.MANIFEST_VERSION, '1.0')
        new JarOutputStream(bytes, manifest).withCloseable { jar ->
            jar.putNextEntry(new JarEntry('evaluation/marker.txt'))
            jar.write('in-flight download'.getBytes(StandardCharsets.UTF_8))
            jar.closeEntry()
        }
        bytes.toByteArray()
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key)
        } else {
            System.setProperty(key, value)
        }
    }
}
