package es.unizar.urlshortener.preview.application

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

@Service
class LinkPreviewService(
    private val store: LinkPreviewStore,
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
        store.insertPending(hash, target)
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
