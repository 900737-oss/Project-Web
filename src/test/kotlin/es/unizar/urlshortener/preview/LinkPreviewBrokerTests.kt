package es.unizar.urlshortener.preview

import com.sun.net.httpserver.HttpServer
import es.unizar.urlshortener.links.application.CreateShortUrl
import es.unizar.urlshortener.preview.application.GetLinkPreview
import es.unizar.urlshortener.preview.application.LinkPreviewStatus
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.rabbitmq.RabbitMQContainer
import java.net.InetSocketAddress
import java.time.Duration

/**
 * Verifies that links publishes the request and preview consumes it through RabbitMQ.
 */
@SpringBootTest
@ActiveProfiles("broker")
@Testcontainers(disabledWithoutDocker = true)
@Import(PreviewRabbitContainer::class)
class LinkPreviewBrokerTests {
    @Autowired
    lateinit var createShortUrl: CreateShortUrl

    @Autowired
    lateinit var getLinkPreview: GetLinkPreview

    @Test
    fun `link creation publishes a request consumed through the broker`() {
        val server = openGraphPage()
        try {
            val created = createShortUrl.create(server.url)

            await().atMost(Duration.ofSeconds(20)).until {
                getLinkPreview.findByHash(created.hash)?.status == LinkPreviewStatus.READY
            }

            val stored = getLinkPreview.findByHash(created.hash)
            assertEquals(LinkPreviewStatus.READY, stored?.status)
            assertEquals("Story", stored?.title)
            assertEquals("https://cdn.example/story.jpg", stored?.image)
            assertEquals("https://example.com/story", stored?.url)
        } finally {
            server.stop()
        }
    }

    private fun openGraphPage(): RunningPage {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val body =
            """
            <html><head>
              <meta property="og:title" content="Story">
              <meta property="og:image" content="https://cdn.example/story.jpg">
              <meta property="og:url" content="https://example.com/story">
            </head></html>
            """.trimIndent().toByteArray()
        http.createContext("/") { exchange ->
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        http.start()
        return RunningPage("http://127.0.0.1:${http.address.port}/") { http.stop(0) }
    }

    private class RunningPage(
        val url: String,
        private val close: () -> Unit,
    ) {
        fun stop() = close()
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PreviewRabbitContainer {
    @Bean
    @ServiceConnection
    fun rabbit(): RabbitMQContainer = RabbitMQContainer("rabbitmq:3.13-management-alpine")
}
