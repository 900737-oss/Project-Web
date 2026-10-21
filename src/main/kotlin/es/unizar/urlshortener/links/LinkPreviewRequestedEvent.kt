package es.unizar.urlshortener.links

import org.springframework.modulith.events.Externalized
import java.util.UUID

const val LINK_PREVIEW_REQUESTED_QUEUE: String = "preview.requested"

const val LINK_PREVIEW_REQUESTED_TARGET: String = "$LINK_PREVIEW_REQUESTED_QUEUE::$LINK_PREVIEW_REQUESTED_QUEUE"

/** Published by links after storing a short URL; preview consumes it through the broker. */
@Externalized(LINK_PREVIEW_REQUESTED_TARGET)
data class LinkPreviewRequestedEvent(
    val hash: String,
    val target: String,
    val eventId: UUID = UUID.randomUUID(),
)
