package es.unizar.urlshortener.preview.application

import org.jmolecules.architecture.hexagonal.SecondaryPort

enum class LinkPreviewStatus {
    PENDING,
    READY,
    FAILED,
}

/** One preview row per short URL. Owned by the preview module. */
data class LinkPreview(
    val hash: String,
    val target: String,
    val status: LinkPreviewStatus,
    val title: String? = null,
    val image: String? = null,
    val url: String? = null,
    val detail: String? = null,
)

@SecondaryPort
interface LinkPreviewStore {
    fun findByHash(hash: String): LinkPreview?

    /**
     * Inserts a [LinkPreviewStatus.PENDING] row.
     * Returns false when [hash] is already stored, without changing that row.
     */
    fun insertPending(
        hash: String,
        target: String,
    ): Boolean
}
