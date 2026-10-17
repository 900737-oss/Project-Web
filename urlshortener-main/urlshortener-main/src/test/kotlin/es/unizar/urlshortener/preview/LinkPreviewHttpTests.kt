package es.unizar.urlshortener.preview

import es.unizar.urlshortener.preview.application.LinkPreview
import es.unizar.urlshortener.preview.application.LinkPreviewStatus
import es.unizar.urlshortener.preview.application.LinkPreviewStore
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LinkPreviewHttpTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var store: LinkPreviewStore

    @Test
    fun `an unknown hash returns problem details`() {
        mockMvc
            .perform(get("/api/preview/missing"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://api.urlshortener.unizar.es/problems/preview-not-found"))
            .andExpect(jsonPath("$.title").value("Preview Not Found"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("No preview is stored for missing"))
            .andExpect(jsonPath("$.instance").value("/api/preview/missing"))
    }

    @Test
    fun `a pending preview returns its status`() {
        store.save(preview("pending-hash", LinkPreviewStatus.PENDING))

        mockMvc
            .perform(get("/api/preview/pending-hash"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hash").value("pending-hash"))
            .andExpect(jsonPath("$.status").value("PENDING"))
    }

    @Test
    fun `a ready preview returns the open graph fields`() {
        store.save(
            preview("ready-hash", LinkPreviewStatus.READY).copy(
                title = "Story",
                image = "https://cdn.example/story.jpg",
                url = "https://example.com/story",
            ),
        )

        mockMvc
            .perform(get("/api/preview/ready-hash"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.title").value("Story"))
            .andExpect(jsonPath("$.image").value("https://cdn.example/story.jpg"))
            .andExpect(jsonPath("$.url").value("https://example.com/story"))
    }

    @Test
    fun `a failed preview returns the failure detail`() {
        store.save(
            preview("failed-hash", LinkPreviewStatus.FAILED).copy(detail = "target unreachable"),
        )

        mockMvc
            .perform(get("/api/preview/failed-hash"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.detail").value("target unreachable"))
    }

    private fun preview(
        hash: String,
        status: LinkPreviewStatus,
    ) = LinkPreview(
        hash = hash,
        target = "https://example.com/$hash",
        status = status,
    )
}
