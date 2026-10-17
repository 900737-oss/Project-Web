package es.unizar.urlshortener.preview

import es.unizar.urlshortener.preview.adapters.events.LinkPreviewRequestedConsumer
import es.unizar.urlshortener.preview.application.FetchLinkPreview
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.modulith.events.Externalized

class LinkPreviewRequestedConsumerTests {
    @Test
    fun `a queued request is fetched`() {
        val fetch = RecordingFetch()
        val consumer = LinkPreviewRequestedConsumer(fetch)
        val event = LinkPreviewRequestedEvent(hash = "abc", target = "https://example.com/story")

        consumer.on(event)

        assertEquals("abc", fetch.hash)
        assertEquals("https://example.com/story", fetch.target)
    }

    @Test
    fun `the request event is externalized to the preview queue`() {
        val externalized = LinkPreviewRequestedEvent::class.java.getAnnotation(Externalized::class.java)

        assertEquals(LINK_PREVIEW_REQUESTED_QUEUE, externalized?.value)
    }

    private class RecordingFetch : FetchLinkPreview {
        var hash: String? = null
        var target: String? = null

        override fun fetch(
            hash: String,
            target: String,
        ) {
            this.hash = hash
            this.target = target
        }
    }
}
