package es.unizar.urlshortener.preview

import es.unizar.urlshortener.preview.application.FetchLinkPreview
import es.unizar.urlshortener.preview.application.LinkPreview
import es.unizar.urlshortener.preview.application.LinkPreviewService
import es.unizar.urlshortener.preview.application.LinkPreviewStatus
import es.unizar.urlshortener.preview.application.LinkPreviewStore
import es.unizar.urlshortener.preview.application.OpenGraphClient
import es.unizar.urlshortener.preview.application.OpenGraphFetchException
import es.unizar.urlshortener.preview.application.OpenGraphMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher

class FetchLinkPreviewTests {
    private val store = MemoryLinkPreviewStore()
    private val client = FakeOpenGraphClient()
    private val fetch: FetchLinkPreview =
        LinkPreviewService(
            store,
            object : ApplicationEventPublisher {
                override fun publishEvent(event: Any) = Unit
            },
            client,
        )

    @Test
    fun `a successful fetch stores ready metadata`() {
        store.insertPending("abc", "https://example.com/story")

        fetch.fetch("abc", "https://example.com/story")

        val stored = store.findByHash("abc")
        assertEquals(LinkPreviewStatus.READY, stored?.status)
        assertEquals("Story", stored?.title)
        assertEquals("https://cdn.example/story.jpg", stored?.image)
        assertEquals("https://example.com/story", stored?.url)
        assertNull(stored?.detail)
        assertEquals(1, client.calls)
    }

    @Test
    fun `a failed fetch stores the failure and does not keep a title`() {
        store.insertPending("abc", "https://example.com/story")
        client.fail = true

        fetch.fetch("abc", "https://example.com/story")

        val stored = store.findByHash("abc")
        assertEquals(LinkPreviewStatus.FAILED, stored?.status)
        assertEquals("Could not fetch https://example.com/story", stored?.detail)
        assertNull(stored?.title)
        assertEquals(1, client.calls)
    }

    @Test
    fun `a ready row is not fetched again`() {
        store.save(
            LinkPreview(
                hash = "abc",
                target = "https://example.com/story",
                status = LinkPreviewStatus.READY,
                title = "Already",
            ),
        )

        fetch.fetch("abc", "https://example.com/story")

        assertEquals("Already", store.findByHash("abc")?.title)
        assertEquals(0, client.calls)
    }

    private class FakeOpenGraphClient : OpenGraphClient {
        var calls: Int = 0
        var fail: Boolean = false

        override fun fetch(target: String): OpenGraphMetadata {
            calls += 1
            if (fail) throw OpenGraphFetchException(target)
            return OpenGraphMetadata(
                title = "Story",
                image = "https://cdn.example/story.jpg",
                url = "https://example.com/story",
            )
        }
    }

    private class MemoryLinkPreviewStore : LinkPreviewStore {
        private val rows = mutableMapOf<String, LinkPreview>()

        override fun findByHash(hash: String): LinkPreview? = rows[hash]

        override fun insertPending(
            hash: String,
            target: String,
        ): Boolean {
            if (rows.containsKey(hash)) return false
            rows[hash] =
                LinkPreview(
                    hash = hash,
                    target = target,
                    status = LinkPreviewStatus.PENDING,
                )
            return true
        }

        override fun save(preview: LinkPreview) {
            rows[preview.hash] = preview
        }
    }
}
