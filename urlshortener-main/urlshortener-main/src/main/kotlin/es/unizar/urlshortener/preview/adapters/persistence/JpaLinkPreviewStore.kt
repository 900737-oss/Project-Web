package es.unizar.urlshortener.preview.adapters.persistence

import es.unizar.urlshortener.preview.application.LinkPreview
import es.unizar.urlshortener.preview.application.LinkPreviewStatus
import es.unizar.urlshortener.preview.application.LinkPreviewStore
import org.springframework.stereotype.Component

@Component
class JpaLinkPreviewStore(
    private val repository: JpaLinkPreviewRepository,
) : LinkPreviewStore {
    override fun findByHash(hash: String): LinkPreview? = repository.findById(hash).map { it.toDomain() }.orElse(null)

    override fun insertPending(
        hash: String,
        target: String,
    ): Boolean {
        if (repository.existsById(hash)) return false
        repository.save(
            LinkPreviewEntity(
                hash = hash,
                target = target,
                status = LinkPreviewStatus.PENDING,
            ),
        )
        return true
    }

    override fun save(preview: LinkPreview) {
        repository.save(preview.toEntity())
    }
}
