package es.unizar.urlshortener.links

import com.jayway.jsonpath.JsonPath.read
import es.unizar.urlshortener.links.application.RedirectCache
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.TimeUnit

@SpringBootTest(properties = ["urlshortener.admin.token=test-admin-token"])
@AutoConfigureMockMvc
class LinkDeactivationTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var redirectCache: RedirectCache

    @Test
    fun `deactivation requires the configured admin token`() {
        val hash = createLink()

        mockMvc
            .perform(post("/api/link/$hash/deactivate"))
            .andExpect(status().isUnauthorized)

        mockMvc
            .perform(post("/api/link/$hash/deactivate").header("X-Admin-Token", "wrong-token"))
            .andExpect(status().isUnauthorized)

        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isTemporaryRedirect)
    }

    @Test
    fun `deactivation invalidates a cached redirect and is idempotent`() {
        val hash = createLink()
        mockMvc.perform(get("/$hash")).andExpect(status().isTemporaryRedirect)
        assertTrue(redirectCache.findTarget(hash).isPresent)

        mockMvc
            .perform(
                post("/api/link/$hash/deactivate")
                    .header("X-Admin-Token", "test-admin-token"),
            ).andExpect(status().isNoContent)
        assertTrue(redirectCache.findTarget(hash).isEmpty)

        mockMvc
            .perform(
                post("/api/link/$hash/deactivate")
                    .header("X-Admin-Token", "test-admin-token"),
            ).andExpect(status().isNoContent)

        mockMvc.perform(get("/$hash")).andExpect(status().isNotFound)
        assertTrue(redirectCache.findTarget(hash).isEmpty)
    }

    @Test
    fun `deactivating an unknown link returns not found`() {
        mockMvc
            .perform(
                post("/api/link/unknown-hash/deactivate")
                    .header("X-Admin-Token", "test-admin-token"),
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `deactivation is consumed by analytics`() {
        val hash = createLink()
        awaitStats(hash, active = true, clicks = 0)

        mockMvc.perform(get("/$hash")).andExpect(status().isTemporaryRedirect)
        awaitStats(hash, active = true, clicks = 1)

        mockMvc
            .perform(
                post("/api/link/$hash/deactivate")
                    .header("X-Admin-Token", "test-admin-token"),
            ).andExpect(status().isNoContent)

        mockMvc.perform(get("/$hash")).andExpect(status().isNotFound)
        awaitStats(hash, active = false, clicks = 1)
    }

    @Test
    fun `unknown and inactive hashes are never cached`() {
        mockMvc.perform(get("/missing-hash")).andExpect(status().isNotFound)
        assertTrue(redirectCache.findTarget("missing-hash").isEmpty)

        val hash = createLink()
        mockMvc
            .perform(
                post("/api/link/$hash/deactivate")
                    .header("X-Admin-Token", "test-admin-token"),
            ).andExpect(status().isNoContent)

        mockMvc.perform(get("/$hash")).andExpect(status().isNotFound)
        assertTrue(redirectCache.findTarget(hash).isEmpty)
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
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
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
