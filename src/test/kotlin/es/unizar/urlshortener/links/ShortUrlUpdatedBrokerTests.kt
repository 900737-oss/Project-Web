package es.unizar.urlshortener.links

import com.jayway.jsonpath.JsonPath.read
import es.unizar.urlshortener.RabbitContainer
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.TimeUnit

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = ["urlshortener.admin.token=test-admin-token"])
@ActiveProfiles("broker")
@AutoConfigureMockMvc
@Import(RabbitContainer::class)
class ShortUrlUpdatedBrokerTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `deactivation reaches analytics through the durable broker`() {
        val hash = createLink()
        awaitStats(hash, active = true, clicks = 0)

        mockMvc.perform(get("/$hash")).andExpect(status().isTemporaryRedirect)
        awaitStats(hash, active = true, clicks = 1)

        mockMvc
            .perform(
                post("/api/link/$hash/deactivate")
                    .header("X-Admin-Token", "test-admin-token"),
            ).andExpect(status().isNoContent)

        awaitStats(hash, active = false, clicks = 1)
    }

    private fun createLink(): String {
        val result =
            mockMvc
                .perform(
                    post("/api/link")
                        .contentType(APPLICATION_FORM_URLENCODED)
                        .param("url", "https://example.com/${System.nanoTime()}"),
                ).andExpect(status().isCreated)
                .andReturn()
        return read(result.response.contentAsString, "$.hash")
    }

    private fun awaitStats(
        hash: String,
        active: Boolean,
        clicks: Int,
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        var last = ""
        while (System.nanoTime() < deadline) {
            val result = mockMvc.perform(get("/api/stats/$hash")).andReturn()
            last = "${result.response.status} ${result.response.contentAsString}"
            if (result.response.status == 200) {
                val body = result.response.contentAsString
                val reportedActive = read<Boolean>(body, "$.active")
                val reportedClicks = read<Number>(body, "$.totalClicks").toInt()
                if (reportedActive == active && reportedClicks == clicks) return
            }
            Thread.sleep(100)
        }
        fail<Unit>("stats for $hash did not reach active=$active totalClicks=$clicks; last=$last")
    }
}
