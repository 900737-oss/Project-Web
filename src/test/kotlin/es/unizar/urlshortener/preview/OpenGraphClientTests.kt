package es.unizar.urlshortener.preview

import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import es.unizar.urlshortener.preview.adapters.fetch.HttpOpenGraphClient
import es.unizar.urlshortener.preview.adapters.fetch.JsoupOpenGraphParser
import es.unizar.urlshortener.preview.application.OpenGraphFetchException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.InetSocketAddress
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OpenGraphClientTests {
    @Test
    fun `fetches open graph metadata from a local page`() {
        val server =
            server { exchange ->
                val body =
                    """
                    <html><head>
                      <meta property="og:title" content="Story">
                      <meta property="og:image" content="https://cdn.example/story.jpg">
                      <meta property="og:url" content="https://example.com/story">
                    </head></html>
                    """.trimIndent().toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        try {
            val metadata = client().fetch(server.url)

            assertEquals("Story", metadata.title)
            assertEquals("https://cdn.example/story.jpg", metadata.image)
            assertEquals("https://example.com/story", metadata.url)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `an open circuit stops calling the target`() {
        val hits = AtomicInteger()
        val server =
            server {
                hits.incrementAndGet()
                it.sendResponseHeaders(500, -1)
                it.close()
            }
        try {
            val openGraph = client(minimumCalls = 3)
            repeat(3) {
                assertThrows<OpenGraphFetchException> { openGraph.fetch(server.url) }
            }
            val failures = hits.get()

            assertThrows<OpenGraphFetchException> { openGraph.fetch(server.url) }

            assertEquals(failures, hits.get())
        } finally {
            server.stop()
        }
    }

    @Test
    fun `follows a relative redirect before parsing metadata`() {
        val hits = AtomicInteger()
        val server =
            server { exchange ->
                hits.incrementAndGet()
                if (exchange.requestURI.path == "/") {
                    exchange.responseHeaders.add("Location", "/story")
                    exchange.sendResponseHeaders(302, -1)
                    exchange.close()
                } else {
                    val body = "<html><title>Redirected story</title></html>".toByteArray()
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
            }
        try {
            assertEquals("Redirected story", client().fetch(server.url).title)
            assertEquals(2, hits.get())
        } finally {
            server.stop()
        }
    }

    @Test
    fun `a response exceeding the body limit is rejected`() {
        val server =
            server { exchange ->
                val body = ByteArray(1_000_001) { 'x'.code.toByte() }
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        try {
            val failure = assertThrows<OpenGraphFetchException> { client().fetch(server.url) }

            assertEquals("Could not fetch ${server.url}", failure.message)
            assertNull(failure.cause)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `a server that does not send headers reaches the request timeout`() {
        val release = CountDownLatch(1)
        val server =
            server { exchange ->
                try {
                    release.await(10, TimeUnit.SECONDS)
                } finally {
                    exchange.close()
                }
            }
        try {
            val failure = assertThrows<OpenGraphFetchException> { client().fetch(server.url) }

            assertInstanceOf(HttpTimeoutException::class.java, failure.cause)
        } finally {
            release.countDown()
            server.stop()
        }
    }

    @Test
    fun `a connection closed before headers is reported as a fetch failure`() {
        val server = server { it.close() }
        try {
            val failure = assertThrows<OpenGraphFetchException> { client().fetch(server.url) }

            assertInstanceOf(IOException::class.java, failure.cause)
            assertEquals("Could not fetch ${server.url}", failure.message)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `a non http target is rejected before downloading`() {
        val failure = assertThrows<OpenGraphFetchException> { client().fetch("file:///private/story.html") }

        assertInstanceOf(IllegalArgumentException::class.java, failure.cause)
        assertEquals("Could not fetch file:///private/story.html", failure.message)
    }

    @Test
    fun `an interrupted download preserves the worker interrupt status`() {
        val openGraph = client()
        val hits = AtomicInteger()
        val server =
            server {
                hits.incrementAndGet()
                it.sendResponseHeaders(200, -1)
                it.close()
            }
        try {
            Executors.newSingleThreadExecutor().use { executor ->
                val worker =
                    executor.submit {
                        Thread.currentThread().interrupt()
                        try {
                            val failure = assertThrows<OpenGraphFetchException> { openGraph.fetch(server.url) }
                            assertInstanceOf(InterruptedException::class.java, failure.cause)
                            assertTrue(Thread.currentThread().isInterrupted)
                        } finally {
                            Thread.interrupted()
                        }
                    }
                worker.get(5, TimeUnit.SECONDS)
            }
            assertEquals(0, hits.get())
        } finally {
            server.stop()
        }
    }

    private fun client(minimumCalls: Int = 5) =
        HttpOpenGraphClient(
            JsoupOpenGraphParser(),
            CircuitBreaker.of(
                "test-open-graph-${UUID.randomUUID()}",
                CircuitBreakerConfig
                    .custom()
                    .slidingWindowSize(minimumCalls)
                    .minimumNumberOfCalls(minimumCalls)
                    .failureRateThreshold(50f)
                    .waitDurationInOpenState(Duration.ofSeconds(30))
                    .build(),
            ),
        )

    private fun server(handler: HttpHandler): RunningServer {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/", handler)
        http.start()
        val port = http.address.port
        return RunningServer("http://127.0.0.1:$port/") { http.stop(0) }
    }

    private class RunningServer(
        val url: String,
        private val close: () -> Unit,
    ) {
        fun stop() = close()
    }
}
