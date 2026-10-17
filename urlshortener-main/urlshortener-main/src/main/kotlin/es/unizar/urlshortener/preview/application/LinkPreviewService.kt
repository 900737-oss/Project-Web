package es.unizar.urlshortener.preview.application

import es.unizar.urlshortener.preview.LinkPreviewRequestedEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

@Service
class LinkPreviewService(
    private val store: LinkPreviewStore,
    private val events: ApplicationEventPublisher,
) : RecordPendingLinkPreview,
    GetLinkPreview {
    @Transactional
    override fun onShortUrlCreated(
        hash: String,
        target: String,
    ): Boolean {
        if (!store.insertPending(hash, target)) return false
        events.publishEvent(LinkPreviewRequestedEvent(hash = hash, target = target))
        log.info { "preview pending for $hash" }
        return true
    }

    @Transactional(readOnly = true)
    override fun findByHash(hash: String): LinkPreview? = store.findByHash(hash)
}
