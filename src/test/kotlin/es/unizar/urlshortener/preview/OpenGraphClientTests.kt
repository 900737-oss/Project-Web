package es.unizar.urlshortener.preview

import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import es.unizar.urlshortener.preview.adapters.fetch.HttpOpenGraphClient
import es.unizar.urlshortener.preview.adapters.fetch.JsoupOpenGraphParser
import es.unizar.urlshortener.preview.application.OpenGraphFetchException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress
import java.time.Duration
import java.util.UUID
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
