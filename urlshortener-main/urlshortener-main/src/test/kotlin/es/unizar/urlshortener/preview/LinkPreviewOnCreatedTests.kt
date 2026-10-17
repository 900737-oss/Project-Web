package es.unizar.urlshortener.preview

import es.unizar.urlshortener.links.ShortUrlCreatedEvent
import es.unizar.urlshortener.preview.application.LinkPreviewStatus
import es.unizar.urlshortener.preview.application.LinkPreviewStore
import es.unizar.urlshortener.preview.application.RecordPendingLinkPreview
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.modulith.test.PublishedEvents
import org.springframework.modulith.test.PublishedEventsExtension
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration

@SpringBootTest
@ExtendWith(PublishedEventsExtension::class)
class LinkPreviewOnCreatedTests {
    @Autowired
    lateinit var publisher: ApplicationEventPublisher

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    lateinit var store: LinkPreviewStore

    @Autowired
    lateinit var recordPendingLinkPreview: RecordPendingLinkPreview

    @Test
    fun `a created short url stores one pending preview and publishes one request`(
        published: PublishedEvents,
    ) {
        publish(ShortUrlCreatedEvent(hash = "preview-created", target = "https://example.com/story"))

        await().atMost(Duration.ofSeconds(5)).until {
            store.findByHash("preview-created")?.status == LinkPreviewStatus.PENDING
        }

        val requests = requestsFor(published, "preview-created")
        assertEquals(1, requests.size)
        assertEquals("https://example.com/story", requests.single().target)
        assertEquals("https://example.com/story", store.findByHash("preview-created")?.target)
    }

    @Test
    fun `a repeated creation does not store or publish again`(
        published: PublishedEvents,
    ) {
        assertTrue(recordPendingLinkPreview.onShortUrlCreated("preview-repeat", "https://example.com/story"))
        assertFalse(recordPendingLinkPreview.onShortUrlCreated("preview-repeat", "https://example.com/other"))

        assertEquals("https://example.com/story", store.findByHash("preview-repeat")?.target)
        assertEquals(LinkPreviewStatus.PENDING, store.findByHash("preview-repeat")?.status)
        assertEquals(1, requestsFor(published, "preview-repeat").size)
    }

    private fun publish(event: ShortUrlCreatedEvent) {
        TransactionTemplate(transactionManager).executeWithoutResult {
            publisher.publishEvent(event)
        }
    }

    private fun requestsFor(
        published: PublishedEvents,
        hash: String,
    ) = published.ofType(LinkPreviewRequestedEvent::class.java).filter { it.hash == hash }
}
