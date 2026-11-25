package es.unizar.urlshortener.clicks

import com.jayway.jsonpath.JsonPath.read
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.TimeUnit

/**
 * Sin perfil broker: el redirect y las stats siguen el camino in-process
 * ClickRecorder + InProcessClickLoggedListener
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClickInProcessIngestionTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `without broker redirect returns 307 and stats report totalClicks`() {
        val hash = createLink()

        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "https://example.com/in-process-click"))

        awaitTotalClicks(hash, clicks = 1)
    }

    private fun createLink(): String {
        val result =
            mockMvc
                .perform(
                    post("/api/link")
                        .contentType(APPLICATION_FORM_URLENCODED)
                        .param("url", "https://example.com/in-process-click"),
                ).andExpect(status().isCreated)
                .andReturn()
        return read(result.response.contentAsString, "$.hash")
    }

    private fun awaitTotalClicks(
        hash: String,
        clicks: Int,
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
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
}
