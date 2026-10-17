package es.unizar.urlshortener.preview

import org.springframework.modulith.events.Externalized
import java.util.UUID

const val LINK_PREVIEW_REQUESTED_QUEUE: String = "preview.requested"

/**
 * Published after a pending preview row is stored.
 * With the broker profile, Modulith externalizes it to [LINK_PREVIEW_REQUESTED_QUEUE] after commit.
 * Delivery is at least once: consumers must ignore an [eventId] they have already processed.
 */
@Externalized(LINK_PREVIEW_REQUESTED_QUEUE)
data class LinkPreviewRequestedEvent(
    val hash: String,
    val target: String,
    val eventId: UUID = UUID.randomUUID(),
)
