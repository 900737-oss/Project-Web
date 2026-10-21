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
    private val client: OpenGraphClient,
) : RecordPendingLinkPreview,
    GetLinkPreview,
    FetchLinkPreview {
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

    @Transactional
    override fun fetch(
        hash: String,
        target: String,
    ) {
        val current = store.findByHash(hash) ?: return
        if (current.status == LinkPreviewStatus.READY) return
        try {
            val metadata = client.fetch(target)
            store.save(
                current.copy(
                    status = LinkPreviewStatus.READY,
                    title = metadata.title,
                    image = metadata.image,
                    url = metadata.url,
                    detail = null,
                ),
            )
            log.info { "preview ready for $hash" }
        } catch (ex: OpenGraphFetchException) {
            store.save(
                current.copy(
                    status = LinkPreviewStatus.FAILED,
                    detail = ex.message,
                ),
            )
            log.info { "preview failed for $hash" }
        }
    }
}
