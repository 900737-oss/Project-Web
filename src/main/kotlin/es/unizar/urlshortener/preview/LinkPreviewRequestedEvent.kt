package es.unizar.urlshortener.preview

import org.springframework.modulith.events.Externalized
import java.util.UUID

const val LINK_PREVIEW_REQUESTED_QUEUE: String = "preview.requested"

/** Modulith AMQP target: exchange::routingKey. Both names match the durable queue. */
const val LINK_PREVIEW_REQUESTED_TARGET: String = "$LINK_PREVIEW_REQUESTED_QUEUE::$LINK_PREVIEW_REQUESTED_QUEUE"

/**
 * Published after a pending preview row is stored.
 * With the broker profile, Modulith externalizes it to [LINK_PREVIEW_REQUESTED_TARGET] after commit.
 * Delivery is at least once: consumers must ignore an [eventId] they have already processed.
 */
@Externalized(LINK_PREVIEW_REQUESTED_TARGET)
data class LinkPreviewRequestedEvent(
    val hash: String,
    val target: String,
    val eventId: UUID = UUID.randomUUID(),
)
