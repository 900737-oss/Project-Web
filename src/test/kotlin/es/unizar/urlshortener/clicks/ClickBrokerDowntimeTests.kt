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
import org.springframework.modulith.events.IncompleteEventPublications
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.rabbitmq.RabbitMQContainer
import java.util.concurrent.TimeUnit

/**
 * Con el broker caido el redirect sigue en 307, al volver, Modulith reenvia el click
 * desde el registro de publicaciones (sin repetir el GET a mano)
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("broker")
@AutoConfigureMockMvc
@Import(RabbitContainer::class)
class ClickBrokerDowntimeTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var rabbit: RabbitMQContainer

    @Autowired
    lateinit var rabbitTemplate: RabbitTemplate

    @Autowired
    lateinit var incompletePublications: IncompleteEventPublications

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Test
    fun `redirect survives broker downtime and recovers clicks when it returns`() {
        val target = "https://example.com/broker-down-${System.nanoTime()}"
        val hash = createLink(target)
        awaitIngested(hash, clicks = 0)

        stopRabbitApp()
        try {
            mockMvc
                .perform(get("/$hash"))
                .andExpect(status().isTemporaryRedirect)
                .andExpect(header().string("Location", target))

            Thread.sleep(500)
            assertEquals(0, clickRows(hash))
            assertEquals(0, currentTotalClicks(hash))
        } finally {
            startRabbitApp()
        }

        awaitRabbitPublishWorks()
        incompletePublications.resubmitIncompletePublications { publication ->
            publication.event is ClickLoggedEvent
        }
        awaitIngested(hash, clicks = 1)
    }

    private fun stopRabbitApp() {
        val result = rabbit.execInContainer("rabbitmqctl", "stop_app")
        check(result.exitCode == 0) { "rabbitmqctl stop_app failed: ${result.stderr}" }
    }

    private fun startRabbitApp() {
        val start = rabbit.execInContainer("rabbitmqctl", "start_app")
        check(start.exitCode == 0) { "rabbitmqctl start_app failed: ${start.stderr}" }
        val await = rabbit.execInContainer("rabbitmqctl", "await_startup")
        check(await.exitCode == 0) { "rabbitmqctl await_startup failed: ${await.stderr}" }
    }

    private fun awaitRabbitPublishWorks() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        var last: Exception? = null
        while (System.nanoTime() < deadline) {
            try {
                rabbitTemplate.execute { channel ->
                    channel.exchangeDeclarePassive(ClickLoggedEvent.EXCHANGE)
                    true
                }
                return
            } catch (ex: Exception) {
                last = ex
                Thread.sleep(200)
            }
        }
        fail<Unit>("RabbitMQ did not accept publishes again: ${last?.message}")
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

    private fun currentTotalClicks(hash: String): Int {
        val result = mockMvc.perform(get("/api/stats/$hash")).andReturn()
        check(result.response.status == 200) { "stats missing for $hash" }
        return read<Number>(result.response.contentAsString, "$.totalClicks").toInt()
    }

    private fun awaitIngested(
        hash: String,
        clicks: Int,
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        var last = ""
        while (System.nanoTime() < deadline) {
            val statsResult = mockMvc.perform(get("/api/stats/$hash")).andReturn()
            val rows = clickRows(hash)
            last = "${statsResult.response.status} ${statsResult.response.contentAsString} clickRows=$rows"
            if (statsResult.response.status == 200) {
                val reportedClicks = read<Number>(statsResult.response.contentAsString, "$.totalClicks").toInt()
                if (reportedClicks == clicks && rows == clicks) return
            }
            Thread.sleep(100)
        }
        fail<Unit>("ingestion for $hash did not reach clicks=$clicks; last=$last")
    }

    private fun clickRows(hash: String): Int = jdbc.queryForObject("select count(*) from click where hash = ?", Int::class.java, hash) ?: 0
}
