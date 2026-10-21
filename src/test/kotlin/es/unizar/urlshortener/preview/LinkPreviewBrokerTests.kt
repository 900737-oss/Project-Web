package es.unizar.urlshortener.preview

import com.rabbitmq.client.ConnectionFactory
import com.sun.net.httpserver.HttpServer
import es.unizar.urlshortener.preview.application.LinkPreviewStatus
import es.unizar.urlshortener.preview.application.LinkPreviewStore
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
import java.util.UUID

/**
 * The application under test only consumes.
 * The request is published with the RabbitMQ client, not with [org.springframework.context.ApplicationEventPublisher].
 */
@SpringBootTest
@ActiveProfiles("broker")
@Testcontainers(disabledWithoutDocker = true)
@Import(PreviewRabbitContainer::class)
class LinkPreviewBrokerTests {
    @Autowired
    lateinit var store: LinkPreviewStore

    @Autowired
    lateinit var rabbit: RabbitMQContainer

    @Test
    fun `a request published straight to the broker marks the pending row ready`() {
        val hash = "preview-from-broker-${UUID.randomUUID()}"
        val server = openGraphPage()
        try {
            store.insertPending(hash, server.url)
            publish(
                """
                {"hash":"$hash","target":"${server.url}","eventId":"${UUID.randomUUID()}"}
                """.trimIndent(),
            )

            await().atMost(Duration.ofSeconds(20)).until {
                val status = store.findByHash(hash)?.status
                status == LinkPreviewStatus.READY || status == LinkPreviewStatus.FAILED
            }

            val stored = store.findByHash(hash)
            assertEquals(LinkPreviewStatus.READY, stored?.status)
            assertEquals("Story", stored?.title)
            assertEquals("https://cdn.example/story.jpg", stored?.image)
            assertEquals("https://example.com/story", stored?.url)
        } finally {
            server.stop()
        }
    }

    private fun publish(json: String) {
        val factory =
            ConnectionFactory().apply {
                host = rabbit.host
                port = rabbit.amqpPort
                username = rabbit.adminUsername
                password = rabbit.adminPassword
            }
        factory.newConnection().use { connection ->
            connection.createChannel().use { channel ->
                val properties =
                    com.rabbitmq.client.AMQP.BasicProperties
                        .Builder()
                        .contentType("application/json")
                        .headers(mapOf("__TypeId__" to LinkPreviewRequestedEvent::class.java.name))
                        .build()
                channel.basicPublish("", LINK_PREVIEW_REQUESTED_QUEUE, properties, json.toByteArray())
            }
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
