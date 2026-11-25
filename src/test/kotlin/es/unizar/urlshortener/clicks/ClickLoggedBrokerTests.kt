package es.unizar.urlshortener.clicks

import com.jayway.jsonpath.JsonPath.read
import es.unizar.urlshortener.RabbitContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.TimeUnit

/**
 * Con perfil broker, el redirect no espera a las stats, ClickLoggedEvent llega por RabbitMQ
 * Una reentrega con el mismo eventId solo se ingiere una vez
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("broker")
@AutoConfigureMockMvc
@Import(RabbitContainer::class)
class ClickLoggedBrokerTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var rabbitTemplate: RabbitTemplate

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Test
    fun `redirect returns 307 immediately and stats rise through the durable broker`() {
        val target = "https://example.com/broker-click-${System.nanoTime()}"
        val hash = createLink(target)
        awaitTotalClicks(hash, clicks = 0)

        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", target))

        awaitTotalClicks(hash, clicks = 1)
    }

    @Test
    fun `a redelivered click through the broker is ingested once`() {
        val hash = createLink("https://example.com/broker-redelivery-${System.nanoTime()}")
        awaitTotalClicks(hash, clicks = 0)

        val event = ClickLoggedEvent(hash = hash)
        repeat(REDELIVERIES) {
            rabbitTemplate.convertAndSend(ClickLoggedEvent.EXCHANGE, ClickLoggedEvent.ROUTING_KEY, event)
        }

        awaitTotalClicks(hash, clicks = 1)
        assertEquals(1, clickRows(hash))
    }

    private fun createLink(target: String): String {
        val result =
            mockMvc
                .perform(
                    post("/api/link")
                        .contentType(APPLICATION_FORM_URLENCODED)
                        .param("url", target),
                ).andExpect(status().isCreated)
                .andReturn()
        return read(result.response.contentAsString, "$.hash")
    }

    private fun awaitTotalClicks(
        hash: String,
        clicks: Int,
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        var last = ""
        while (System.nanoTime() < deadline) {
            val result = mockMvc.perform(get("/api/stats/$hash")).andReturn()
            last = "${result.response.status} ${result.response.contentAsString}"
            if (result.response.status == 200) {
                val reportedClicks = read<Number>(result.response.contentAsString, "$.totalClicks").toInt()
                if (reportedClicks == clicks) return
            }
            Thread.sleep(100)
        }
        fail<Unit>("stats for $hash did not reach totalClicks=$clicks; last=$last")
    }

    private fun clickRows(hash: String): Int = jdbc.queryForObject("select count(*) from click where hash = ?", Int::class.java, hash) ?: 0

    private companion object {
        const val REDELIVERIES = 3
    }
}
