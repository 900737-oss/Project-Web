package es.unizar.urlshortener.clicks

import org.springframework.modulith.events.Externalized
import java.time.Instant
import java.util.UUID

/**
 * Delivery is at least once: consumers must ignore an [eventId] they have already processed.
 * With the broker profile, Modulith externalizes it to a durable RabbitMQ exchange.
 */
@Externalized(ClickLoggedEvent.TARGET)
data class ClickLoggedEvent(
    val hash: String,
    val occurredAt: Instant = Instant.now(),
    val eventId: UUID = UUID.randomUUID(),
) {
    companion object {
        const val EXCHANGE = "urlshortener.click-logged"
        const val ROUTING_KEY = "clicks.ClickLoggedEvent"
        const val TARGET = "$EXCHANGE::$ROUTING_KEY"
    }
}
