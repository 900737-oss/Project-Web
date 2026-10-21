package es.unizar.urlshortener.preview

import es.unizar.urlshortener.links.LinkPreviewRequestedEvent
import es.unizar.urlshortener.links.ShortUrlCreatedEvent
import es.unizar.urlshortener.links.application.CreateShortUrl
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

    @Autowired
    lateinit var createShortUrl: CreateShortUrl

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
    fun `recording a pending preview is idempotent`() {
        val hash = "preview-repeat-${UUID.randomUUID()}"
        assertTrue(recordPendingLinkPreview.onShortUrlCreated(hash, "https://example.com/story"))
        assertFalse(recordPendingLinkPreview.onShortUrlCreated(hash, "https://example.com/other"))

        assertEquals("https://example.com/story", store.findByHash(hash)?.target)
        assertEquals(LinkPreviewStatus.PENDING, store.findByHash(hash)?.status)
    }

    @Test
    fun `creating a short url publishes the preview request from links`(published: PublishedEvents) {
        val created = createShortUrl.create("https://example.com/${UUID.randomUUID()}")

        val request = published.ofType(LinkPreviewRequestedEvent::class.java).single { it.hash == created.hash }
        assertEquals(created.target, request.target)
    }

    private fun publish(event: ShortUrlCreatedEvent) {
        TransactionTemplate(transactionManager).executeWithoutResult {
            publisher.publishEvent(event)
        }
    }
}
