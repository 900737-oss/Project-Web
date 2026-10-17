package es.unizar.urlshortener.preview

import java.util.UUID

/**
 * Published after a pending preview row is stored.
 * Delivery is at least once: consumers must ignore an [eventId] they have already processed.
 */
data class LinkPreviewRequestedEvent(
    val hash: String,
    val target: String,
    val eventId: UUID = UUID.randomUUID(),
)
