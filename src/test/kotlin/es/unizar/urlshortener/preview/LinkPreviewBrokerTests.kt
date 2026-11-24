package es.unizar.urlshortener.preview

import com.sun.net.httpserver.HttpServer
import es.unizar.urlshortener.links.application.CreateShortUrl
import es.unizar.urlshortener.preview.application.GetLinkPreview
import es.unizar.urlshortener.preview.application.LinkPreviewStatus
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.rabbitmq.RabbitMQContainer
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * Verifies that links publishes the request and preview consumes it through RabbitMQ.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("broker")
@Testcontainers(disabledWithoutDocker = true)
@Import(PreviewRabbitContainer::class)
class LinkPreviewBrokerTests {
    @Autowired
    lateinit var createShortUrl: CreateShortUrl

    @Autowired
    lateinit var getLinkPreview: GetLinkPreview

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jsonMapper: JsonMapper

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

    @Test
    fun `a failed preview does not delay link creation or break redirection`() {
        val page = BlockedFailingPage()
        val creation = createLinkAsynchronously(page.target)
        try {
            page.awaitFetchStarted()
            val hash = assertLinkCreatedWithoutWaiting(creation)
            assertRedirectsToTarget(hash, page.target)
            assertPreviewIsPending(hash)
        } finally {
            page.allowFailure()
        }

        try {
            val hash = jsonMapper.readTree(creation.get().response.contentAsString).get("hash").asText()
            awaitPreviewFailed(hash)
        } finally {
            page.stop()
        }
    }

    private fun createLinkAsynchronously(target: String): FutureTask<MvcResult> {
        val creation =
            FutureTask {
                val result =
                    mockMvc.perform(
                        post("/api/link")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("url", target),
                    )
                result.andReturn()
            }
        Thread(creation).start()
        return creation
    }

    private fun assertLinkCreatedWithoutWaiting(creation: FutureTask<MvcResult>): String {
        val created = creation.get(2, TimeUnit.SECONDS)
        assertEquals(201, created.response.status)
        val location = created.response.getHeader(HttpHeaders.LOCATION)
        assertNotNull(location)
        val responseJson = jsonMapper.readTree(created.response.contentAsString)
        val hash = responseJson.get("hash").asText()
        assertEquals(location, responseJson.get("url").asText())
        assertEquals(hash, location!!.substringAfterLast('/'))
        return hash
    }

    private fun assertRedirectsToTarget(
        hash: String,
        target: String,
    ) {
        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .header()
                    .string(HttpHeaders.LOCATION, target),
            )
    }

    private fun assertPreviewIsPending(hash: String) {
        mockMvc
            .perform(get("/api/preview/$hash"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PENDING"))
    }

    private fun awaitPreviewFailed(hash: String) {
        await().atMost(Duration.ofSeconds(20)).until {
            val previewResponse = mockMvc.perform(get("/api/preview/$hash")).andReturn().response
            previewResponse.status == 200 &&
                jsonMapper.readTree(previewResponse.contentAsString).get("status").toString() == "\"FAILED\""
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

    private class BlockedFailingPage {
        private val fetchStarted = CountDownLatch(1)
        private val allowFetchToFail = CountDownLatch(1)
        private val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val target = "http://127.0.0.1:${http.address.port}/unreadable"

        init {
            http.createContext("/") { exchange ->
                fetchStarted.countDown()
                allowFetchToFail.await(10, TimeUnit.SECONDS)
                exchange.sendResponseHeaders(500, -1)
                exchange.close()
            }
            http.start()
        }

        fun awaitFetchStarted() {
            assertEquals(true, fetchStarted.await(5, TimeUnit.SECONDS), "preview fetch did not start")
        }

        fun allowFailure() = allowFetchToFail.countDown()

        fun stop() = http.stop(0)
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PreviewRabbitContainer {
    @Bean
    @ServiceConnection
    fun rabbit(): RabbitMQContainer = RabbitMQContainer("rabbitmq:3.13-management-alpine")
}
