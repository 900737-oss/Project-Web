package es.unizar.urlshortener.links

import com.jayway.jsonpath.JsonPath.read
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(properties = ["urlshortener.admin.token=test-admin-token"])
@AutoConfigureMockMvc
class LinkDeactivationTests {
    @Autowired
    lateinit var mockMvc: MockMvc

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

        mockMvc
            .perform(
                post("/api/link/$hash/deactivate")
                    .header("X-Admin-Token", "test-admin-token"),
            ).andExpect(status().isNoContent)

        mockMvc
            .perform(
                post("/api/link/$hash/deactivate")
                    .header("X-Admin-Token", "test-admin-token"),
            ).andExpect(status().isNoContent)

        mockMvc.perform(get("/$hash")).andExpect(status().isNotFound)
    }

    @Test
    fun `deactivating an unknown link returns not found`() {
        mockMvc
            .perform(
                post("/api/link/unknown-hash/deactivate")
                    .header("X-Admin-Token", "test-admin-token"),
            ).andExpect(status().isNotFound)
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
}
