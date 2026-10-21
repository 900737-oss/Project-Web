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
import java.util.UUID

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
    fun `a created short url stores one pending preview`() {
        val hash = "preview-created-${UUID.randomUUID()}"
        publish(ShortUrlCreatedEvent(hash = hash, target = "https://example.com/story"))

        await().atMost(Duration.ofSeconds(5)).until {
            store.findByHash(hash)?.status == LinkPreviewStatus.PENDING
        }

        assertEquals("https://example.com/story", store.findByHash(hash)?.target)
    }

    @Test
    fun `recording a pending preview publishes one request and ignores repeats`(published: PublishedEvents) {
        val hash = "preview-repeat-${UUID.randomUUID()}"
        assertTrue(recordPendingLinkPreview.onShortUrlCreated(hash, "https://example.com/story"))
        assertFalse(recordPendingLinkPreview.onShortUrlCreated(hash, "https://example.com/other"))

        assertEquals("https://example.com/story", store.findByHash(hash)?.target)
        assertEquals(LinkPreviewStatus.PENDING, store.findByHash(hash)?.status)
        val requests = published.ofType(LinkPreviewRequestedEvent::class.java).filter { it.hash == hash }
        assertEquals(1, requests.size)
        assertEquals("https://example.com/story", requests.single().target)
    }

    private fun publish(event: ShortUrlCreatedEvent) {
        TransactionTemplate(transactionManager).executeWithoutResult {
            publisher.publishEvent(event)
        }
    }
}
